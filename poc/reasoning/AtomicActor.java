package grakn.common.poc.reasoning;

import grakn.common.concurrent.actor.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static grakn.common.collection.Collections.list;

public class AtomicActor extends ReasoningActor<AtomicActor> {
    private final Logger LOG;

    private final String name;
    private final Long traversalPattern;
    private final long traversalSize;
    // TODO EH???? what is the below comment
    // TODO note that this can be many to one, and is not catered for yet (ie. request followed the same request)
    private final List<Actor<RuleActor>> ruleActors;

    public AtomicActor(final Actor<AtomicActor> self, final ActorRegistry actorRegistry, final Long traversalPattern, final long traversalSize, final List<List<Long>> rules) {
        super(self, actorRegistry);
        LOG = LoggerFactory.getLogger(AtomicActor.class.getSimpleName() + "-" + traversalPattern);

        name = "AtomicActor(pattern: " + traversalPattern + ")";
        this.traversalPattern = traversalPattern;
        this.traversalSize = traversalSize;
        ruleActors = registerRuleActors(actorRegistry, rules);
    }

    private List<Actor<RuleActor>> registerRuleActors(final ActorRegistry actorRegistry, final List<List<Long>> rules) {
        final List<Actor<RuleActor>> ruleActors = new ArrayList<>();
        for (List<Long> rule : rules) {
            Actor<RuleActor> ruleActor = actorRegistry.registerRule(rule, pattern ->
                    child(actor -> new RuleActor(actor, actorRegistry, pattern, 1L))
            );
            ruleActors.add(ruleActor);
        }
        return ruleActors;
    }

    @Override
    public void receiveRequest(final Request fromUpstream) {
        LOG.debug("Received fromUpstream in: " + name);

        initialiseResponseProducer(fromUpstream);

        Plan responsePlan = getResponsePlan(fromUpstream);

        if (noMoreAnswersPossible(fromUpstream)) respondExhaustedToUpstream(fromUpstream, responsePlan);
        else {
            // TODO if we want batching, we increment by as many as are requested
            incrementRequestsFromUpstream(fromUpstream);

            if (upstreamHasRequestsOutstanding(fromUpstream)) {
                traverseAndRespond(fromUpstream, responsePlan);
            }

            if (upstreamHasRequestsOutstanding(fromUpstream) && downstreamAvailable(fromUpstream)) {
                requestFromAvailableDownstream(fromUpstream);
            }
        }
    }

    @Override
    public void receiveAnswer(final Response.Answer fromDownstream) {
        LOG.debug("Received fromDownstream response in: " + name);
        Request sentDownstream = fromDownstream.sourceRequest();
        Request fromUpstream = requestRouter.get(sentDownstream);

        decrementRequestsToDownstream(fromUpstream);

        Plan forwardingPlan = forwardingPlan(fromDownstream);

        // TODO fix accessing state
        if (answerSource(fromDownstream).state instanceof AtomicActor) {
            registerTraversal(fromUpstream, computeAnswer(fromDownstream.partialAnswers));
            traverseAndRespond(fromUpstream, forwardingPlan);
            registerDownstreamRules(
                    fromUpstream,
                    fromDownstream.plan,
                    fromDownstream.partialAnswers,
                    fromDownstream.constraints,
                    fromDownstream.unifiers
            );
        } else if (answerSource(fromDownstream).state instanceof RuleActor) {
            bufferAnswers(fromUpstream, Arrays.asList(computeAnswer(fromDownstream.partialAnswers)));
            respondAnswersToUpstream(
                    fromUpstream,
                    forwardingPlan,
                    Arrays.asList(),
                    fromUpstream.constraints,
                    fromUpstream.unifiers,
                    responseProducers.get(fromUpstream),
                    forwardingPlan.currentStep()
            );
        } else {
            throw new RuntimeException("Unhandled downstream actor of type " +
                    sentDownstream.plan.nextStep().state.getClass().getSimpleName());
        }

        if (upstreamHasRequestsOutstanding(fromUpstream) && downstreamAvailable(fromUpstream)) {
            requestFromAvailableDownstream(fromUpstream);
        }

        if (noMoreAnswersPossible(fromUpstream)) respondExhaustedToUpstream(fromUpstream, getResponsePlan(fromUpstream));
    }

    @Override
    public void receiveExhausted(final Response.Exhausted fromDownstream) {
        LOG.debug("Received fromDownstream response in: " + name);
        Request sentDownstream = fromDownstream.sourceRequest();
        Request fromUpstream = requestRouter.get(sentDownstream);
        decrementRequestsToDownstream(fromUpstream);

        downstreamExhausted(fromUpstream, sentDownstream);

        Plan responsePlan = getResponsePlan(fromUpstream);
        if (noMoreAnswersPossible(fromUpstream)) {
            respondExhaustedToUpstream(fromUpstream, responsePlan);
        } else {
            traverseAndRespond(fromUpstream, responsePlan);

            if (upstreamHasRequestsOutstanding(fromUpstream) && downstreamAvailable(fromUpstream)) {
                requestFromAvailableDownstream(fromUpstream);
            }
        }
    }

    @Override
    void requestFromAvailableDownstream(final Request fromUpstream) {
        ResponseProducer responseProducer = responseProducers.get(fromUpstream);
        Request toDownstream = responseProducer.getAvailableDownstream();
        Actor<? extends ReasoningActor<?>> downstream = toDownstream.plan.currentStep();
        responseProducer.incrementRequestsToDownstream();
        // TODO we may overwrite if multiple identical requests are sent, when to clean up?
        requestRouter.put(toDownstream, fromUpstream);

        LOG.debug("Requesting from downstream in: " + name);
        downstream.tell(actor -> actor.receiveRequest(toDownstream));
    }

    @Override
    void respondAnswersToUpstream(
            final Request request,
            final Plan plan,
            final List<Long> partialAnswers,
            final List<Object> constraints,
            final List<Object> unifiers,
            final ResponseProducer responseProducer,
            final Actor<? extends ReasoningActor<?>> upstream
    ) {
        // send as many answers as possible to upstream
        for (int i = 0; i < Math.min(responseProducer.requestsFromUpstream(), responseProducer.bufferedSize()); i++) {
            Long answer = responseProducer.bufferTake();
            List<Long> newAnswers = list(partialAnswers, answer);
            Response.Answer responseAnswer = new Response.Answer(
                    request,
                    plan,
                    newAnswers,
                    constraints,
                    unifiers
            );

            LOG.debug("Responding answer to upstream from actor: " + name);
            upstream.tell((actor) -> actor.receiveAnswer(responseAnswer));
            responseProducer.decrementRequestsFromUpstream();
        }
    }

    @Override
    void respondExhaustedToUpstream(final Request request, final Plan responsePlan) {
        Actor<? extends ReasoningActor<?>> upstream = responsePlan.currentStep();
        Response.Exhausted responseExhausted = new Response.Exhausted(request, responsePlan);
        LOG.debug("Responding Exhausted to upstream from actor: " + name);
        upstream.tell((actor) -> actor.receiveExhausted(responseExhausted));
    }

    private void initialiseResponseProducer(final Request request) {
        if (!responseProducers.containsKey(request)) {
            ResponseProducer responseProducer = new ResponseProducer();
            responseProducers.put(request, responseProducer);

            boolean hasDownstream = request.plan.nextStep() != null;
            if (hasDownstream) {
                Plan nextStep = request.plan.toNextStep();
                Request toDownstream = new Request(
                        nextStep,
                        request.partialAnswers,
                        request.constraints,
                        request.unifiers
                );
                responseProducer.addAvailableDownstream(toDownstream);
            } else {
                registerTraversal(request, computeAnswer(request.partialAnswers));
                registerDownstreamRules(
                        request,
                        request.plan,
                        request.partialAnswers,
                        request.constraints,
                        request.unifiers
                );
            }
        }
    }

    private void traverseAndRespond(final Request fromUpstream, final Plan responsePlan) {
        ResponseProducer responseProducer = responseProducers.get(fromUpstream);
        List<Long> answers = produceTraversalAnswers(responseProducer);
        bufferAnswers(fromUpstream, answers);
        respondAnswersToUpstream(
                fromUpstream,
                responsePlan,
                fromUpstream.partialAnswers,
                fromUpstream.constraints,
                fromUpstream.unifiers,
                responseProducer,
                responsePlan.currentStep()
        );
    }

    private List<Long> produceTraversalAnswers(final ResponseProducer responseProducer) {
        Iterator<Long> traversalProducer = responseProducer.getOneTraversalProducer();
        if (traversalProducer != null) {
            // TODO could do batch traverse, or retrieve answers from multiple traversals
            Long answer = traversalProducer.next();
            if (!traversalProducer.hasNext()) responseProducer.removeTraversalProducer(traversalProducer);
            answer += this.traversalPattern;
            return Arrays.asList(answer);
        }
        return Arrays.asList();
    }

    private void registerTraversal(final Request request, final Long answer) {
        Iterator<Long> traversal = (new MockTransaction(traversalSize, 1)).query(answer);
        if (traversal.hasNext()) responseProducers.get(request).addTraversalProducer(traversal);
    }

    private void bufferAnswers(final Request request, final List<Long> answers) {
        responseProducers.get(request).bufferAnswers(answers);
    }

    private void registerDownstreamRules(final Request request, final Plan basePlan, final List<Long> partialAnswers,
                                         final List<Object> constraints, final List<Object> unifiers) {
        for (Actor<RuleActor> ruleActor : ruleActors) {
            Plan toRule = basePlan.addStep(ruleActor).toNextStep();
            Request toDownstream = new Request(toRule, partialAnswers, constraints, unifiers);
            responseProducers.get(request).addAvailableDownstream(toDownstream);
        }
    }

    private boolean upstreamHasRequestsOutstanding(final Request fromUpstream) {
        ResponseProducer responseProducer = responseProducers.get(fromUpstream);
        return responseProducer.requestsFromUpstream() > responseProducer.requestsToDownstream() + responseProducer.bufferedSize();
    }

    private boolean noMoreAnswersPossible(final Request fromUpstream) {
        return responseProducers.get(fromUpstream).noMoreAnswersPossible();
    }

    private void incrementRequestsFromUpstream(final Request fromUpstream) {
        responseProducers.get(fromUpstream).incrementRequestsFromUpstream();
    }

    private void decrementRequestsToDownstream(final Request fromUpstream) {
        responseProducers.get(fromUpstream).decrementRequestsToDownstream();
    }

    private Actor<? extends ReasoningActor<?>> answerSource(final Response.Answer answer) {
        return answer.sourceRequest().plan.currentStep();
    }

    private Plan getResponsePlan(final Request fromUpstream) {
        return fromUpstream.plan.truncate().endStepCompleted();
    }

    private Plan forwardingPlan(final Response.Answer fromDownstream) {
        return fromDownstream.plan.endStepCompleted();
    }

    private boolean downstreamAvailable(final Request fromUpstream) {
        return !responseProducers.get(fromUpstream).downstreamExhausted();
    }

    private void downstreamExhausted(final Request fromUpstream, final Request sentDownstream) {
        responseProducers.get(fromUpstream).downstreamExhausted(sentDownstream);
    }

    private Long computeAnswer(final List<Long> partialAnswers) {
        return partialAnswers.stream().reduce(0L, (acc, v) -> acc + v);
    }
}

