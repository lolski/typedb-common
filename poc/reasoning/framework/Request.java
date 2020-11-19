package grakn.common.poc.reasoning.framework;


import grakn.common.concurrent.actor.Actor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static grakn.common.collection.Collections.list;

public class Request {
    private final Path path;
    private final List<Long> partialConceptMap;
    private final List<Object> unifiers;
    private final Derivations partialDerivations;

    public Request(Path path,
                   List<Long> partialConceptMap,
                   List<Object> unifiers,
                   Derivations partialDerivations) {
        this.path = path;
        this.partialConceptMap = partialConceptMap;
        this.unifiers = unifiers;
        this.partialDerivations = partialDerivations;
    }

    public Path path() {
        return path;
    }

    @Nullable
    public Actor<? extends Execution<?>> sender() {
        if (path.path.size() < 2) {
            return null;
        }
        return path.path.get(path.path.size() - 2);
    }

    public Actor<? extends Execution<?>> receiver() {
        return path.path.get(path.path.size() - 1);
    }

    public List<Long> partialConceptMap() {
        return partialConceptMap;
    }

    public List<Object> unifiers() {
        return unifiers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return Objects.equals(path, request.path) &&
                Objects.equals(partialConceptMap, request.partialConceptMap()) &&
                Objects.equals(unifiers, request.unifiers());
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, partialConceptMap, unifiers);
    }

    @Override
    public String toString() {
        return "Req(send=" + (sender() == null ? "<none>" : sender().state.name) + ", pAns=" + partialConceptMap + ")";
    }

    public Derivations partialDerivations() {
        return partialDerivations;
    }

    public static class Path {
        final List<Actor<? extends Execution<?>>> path;

        public Path(Actor<? extends Execution<?>> sender) {
            this(list(sender));
        }

        private Path(List<Actor<? extends Execution<?>>> path) {
            assert !path.isEmpty() : "Path cannot be empty";
            this.path = path;
        }

        public Path append(Actor<? extends Execution<?>> actor) {
            List<Actor<? extends Execution<?>>> appended = new ArrayList<>(path);
            appended.add(actor);
            return new Path(appended);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Path path1 = (Path) o;
            return Objects.equals(path, path1.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }
}