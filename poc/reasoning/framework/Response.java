package grakn.common.poc.reasoning.framework;

import java.util.List;

public interface Response {
    Request sourceRequest();

    boolean isAnswer();
    boolean isExhausted();

    default Response.Answer asAnswer() {
        throw new ClassCastException("Cannot cast " + this.getClass().getSimpleName() + " to " + Response.Answer.class.getSimpleName());
    }

    default Response.Exhausted asExhausted() {
        throw new ClassCastException("Cannot cast " + this.getClass().getSimpleName() + " to " + Response.Exhausted.class.getSimpleName());
    }

    class Answer implements Response {
        private final Request sourceRequest;
        private final List<Long> conceptMap;
        private final List<Object> unifiers;

        private final String patternAnswered;
        private final Derivations derivations;

        public Answer(Request sourceRequest,
                      List<Long> conceptMap,
                      List<Object> unifiers,
                      String patternAnswered,
                      Derivations derivations) {
            this.sourceRequest = sourceRequest;
            this.conceptMap = conceptMap;
            this.unifiers = unifiers;
            this.patternAnswered = patternAnswered;
            this.derivations = derivations;
        }

        @Override
        public Request sourceRequest() {
            return sourceRequest;
        }

        public List<Long> conceptMap() {
            return conceptMap;
        }

        public List<Object> unifiers() {
            return unifiers;
        }

        public Derivations derivations() {
            return derivations;
        }

        public boolean isInferred() {
            return !derivations.equals(Derivations.EMPTY);
        }

        @Override
        public boolean isAnswer() { return true; }

        @Override
        public boolean isExhausted() { return false; }

        @Override
        public Response.Answer asAnswer() {
            return this;
        }

        @Override
        public String toString() {
            return "\nAnswer{" +
                    "\nsourceRequest=" + sourceRequest +
                    ",\n partialConceptMap=" + conceptMap +
                    ",\n unifiers=" + unifiers +
                    ",\n patternAnswered=" + patternAnswered +
                    ",\n derivations=" + derivations +
                    '}';
        }
    }

    class Exhausted implements Response {
        private final Request sourceRequest;

        public Exhausted(Request sourceRequest) {
            this.sourceRequest = sourceRequest;
        }

        @Override
        public Request sourceRequest() {
            return sourceRequest;
        }

        @Override
        public boolean isAnswer() { return false; }

        @Override
        public boolean isExhausted() { return true; }

        @Override
        public Response.Exhausted asExhausted() {
            return this;
        }


        @Override
        public String toString() {
            return "Exhausted{" +
                    "sourceRequest=" + sourceRequest +
                    '}';
        }
    }
}