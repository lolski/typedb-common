package grakn.common.poc.reasoning.mock;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static grakn.common.collection.Collections.extend;

/**
 * Pretend to compute an iterator of longs, which just adds up numbers and returns
 * them in intervals.
 *
 * Repeatedly calling query() will repeat the same calculation loop in a new iterator
 */
public class MockTransaction {
    private static AtomicLong traversalPatternDifferentiator = new AtomicLong(0L);
    private final long computeLength;
    private final Long traversalPattern;
    private final int answerInterval;

    public MockTransaction(long computeLength, Long traversalPattern, int answerInterval) {
        long differentiator = traversalPatternDifferentiator.getAndAdd(100);
        this.computeLength = computeLength + traversalPattern + differentiator;
        this.traversalPattern = traversalPattern + differentiator;
        this.answerInterval = answerInterval;

    }

    public Iterator<List<Long>> query(final List<Long> partialAnswer) {
        return new Iterator<List<Long>>() {
            long count = traversalPattern;

            @Override
            public boolean hasNext() {
                return count < computeLength;
            }

            @Override
            public List<Long> next() {
                while (count < computeLength) {
                    if (count % answerInterval == 0) {
                        List<Long> answer = extend(partialAnswer, count);
                        count++;
                        return answer;
                    } else {
                        count++;
                    }
                }
                return null;
            }
        };
    }
}
