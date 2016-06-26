package javaslang.collection;

import javaslang.*;
import javaslang.JmhRunner.*;
import org.junit.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.State;
import scala.math.Ordering;
import scala.math.Ordering$;
import scalaz.*;

import java.util.Collections;

import static javaslang.JmhRunner.*;

public class PriorityQueueBenchmark {
    static final Array<Class<?>> CLASSES = Array.of(
            Enqueue.class,
            Dequeue.class,
            Sort.class
    );

    @Test
    public void testAsserts() {
        JmhRunner.runAndReport(CLASSES, 0, 1, 1, 1, PrintGc.Disable, Assertions.Enable); // runDebug fails with stack overflow for Scalaz, because it cannot update the jvm args, if not forked
    }

    public static void main(String... args) {
        JmhRunner.runNormal(CLASSES);
    }

    @State(Scope.Benchmark)
    public static class Base {
        protected static final Ordering<Integer> SCALA_ORDERING = Ordering$.MODULE$.comparatorToOrdering(Integer::compareTo);
        protected static final Order<Integer> SCALAZ_ORDER = Order$.MODULE$.fromScalaOrdering(SCALA_ORDERING);

        @Param({ "10", "100", "1000" })
        public int CONTAINER_SIZE;

        int EXPECTED_AGGREGATE;
        Integer[] ELEMENTS;

        scalaz.Heap<Integer> scalazPersistent = scalaz.Heap.Empty$.MODULE$.apply();
        javaslang.collection.PriorityQueue<Integer> slangPersistent = javaslang.collection.PriorityQueue.empty();

        @Setup
        public void setup() {
            ELEMENTS = getRandomValues(CONTAINER_SIZE, 0);
            EXPECTED_AGGREGATE = Iterator.of(ELEMENTS).reduce(JmhRunner::aggregate);

            for (Integer element : ELEMENTS) {
                scalazPersistent = scalazPersistent.insert(element, SCALAZ_ORDER);
            }
            slangPersistent = javaslang.collection.PriorityQueue.of(ELEMENTS);

            assert scalazPersistent.size() == CONTAINER_SIZE
                   && slangPersistent.size() == CONTAINER_SIZE;
        }
    }

    public static class Enqueue extends Base {
        @Benchmark
        @SuppressWarnings({ "Convert2streamapi", "ManualArrayToCollectionCopy" })
        public Object java_mutable() {
            final java.util.PriorityQueue<Integer> values = new java.util.PriorityQueue<>(ELEMENTS.length);
            for (Integer element : ELEMENTS) {
                values.add(element);
            }
            assert values.size() == CONTAINER_SIZE;
            return values;
        }

        @Benchmark
        @SuppressWarnings({ "Convert2streamapi", "ManualArrayToCollectionCopy" })
        public Object java_blocking_mutable() {
            final java.util.concurrent.PriorityBlockingQueue<Integer> values = new java.util.concurrent.PriorityBlockingQueue<>(ELEMENTS.length);
            for (Integer element : ELEMENTS) {
                values.add(element);
            }
            assert values.size() == CONTAINER_SIZE;
            return values;
        }

        @Benchmark
        public Object scala_mutable() {
            final scala.collection.mutable.PriorityQueue<Integer> values = new scala.collection.mutable.PriorityQueue<>(SCALA_ORDERING);
            for (Integer element : ELEMENTS) {
                values.$plus$eq(element);
            }
            assert values.size() == CONTAINER_SIZE;
            return values;
        }

        @Benchmark
        public Object scalaz_persistent() {
            scalaz.Heap<Integer> values = scalaz.Heap.Empty$.MODULE$.apply();
            for (Integer element : ELEMENTS) {
                values = values.insert(element, SCALAZ_ORDER);
            }
            assert values.size() == CONTAINER_SIZE;
            return values;
        }

        @Benchmark
        public Object slang_persistent() {
            javaslang.collection.PriorityQueue<Integer> values = javaslang.collection.PriorityQueue.empty();
            for (Integer element : ELEMENTS) {
                values = values.enqueue(element);
            }
            assert values.size() == CONTAINER_SIZE;
            return values;
        }
    }

    @SuppressWarnings("Convert2MethodRef")
    public static class Dequeue extends Base {
        @State(Scope.Thread)
        public static class Initialized {
            java.util.PriorityQueue<Integer> javaMutable = new java.util.PriorityQueue<>();
            java.util.concurrent.PriorityBlockingQueue<Integer> javaBlockingMutable = new java.util.concurrent.PriorityBlockingQueue<>();
            scala.collection.mutable.PriorityQueue<Integer> scalaMutable = new scala.collection.mutable.PriorityQueue<>(SCALA_ORDERING);

            @Setup(Level.Invocation)
            public void initializeMutable(Base state) {
                Collections.addAll(javaMutable, state.ELEMENTS);
                Collections.addAll(javaBlockingMutable, state.ELEMENTS);
                for (Integer element : state.ELEMENTS) {
                    scalaMutable.$plus$eq(element);
                }

                assert javaMutable.size() == state.CONTAINER_SIZE
                       && javaBlockingMutable.size() == state.CONTAINER_SIZE
                       && scalaMutable.size() == state.CONTAINER_SIZE;
            }

            @TearDown(Level.Invocation)
            public void tearDown() {
                javaMutable.clear();
                javaBlockingMutable.clear();
                scalaMutable.clear();
            }
        }

        @Benchmark
        public Object java_mutable(Initialized state) {
            final java.util.PriorityQueue<Integer> values = state.javaMutable;

            int aggregate = 0;
            for (; !values.isEmpty(); values.poll()) {
                aggregate ^= values.peek();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object java_blocking_mutable(Initialized state) {
            final java.util.concurrent.PriorityBlockingQueue<Integer> values = state.javaBlockingMutable;

            int aggregate = 0;
            for (; !values.isEmpty(); values.poll()) {
                aggregate ^= values.peek();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object scala_mutable(Initialized state) {
            final scala.collection.mutable.PriorityQueue<Integer> values = state.scalaMutable;

            int aggregate = 0;
            while (!values.isEmpty()) {
                aggregate ^= values.dequeue();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object scalaz_persistent() {
            scalaz.Heap<Integer> values = scalazPersistent;

            int aggregate = 0;
            while (!values.isEmpty()) {
                final scala.Tuple2<Integer, scalaz.Heap<Integer>> uncons = values.uncons().get();
                aggregate ^= uncons._1;
                values = uncons._2;
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object slang_persistent() {
            javaslang.collection.PriorityQueue<Integer> values = slangPersistent;

            int aggregate = 0;
            while (!values.isEmpty()) {
                final Tuple2<Integer, javaslang.collection.PriorityQueue<Integer>> dequeue = values.dequeue();
                aggregate ^= dequeue._1;
                values = dequeue._2;
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }
    }

    @SuppressWarnings("Convert2MethodRef")
    public static class Sort extends Base {
        @Benchmark
        @SuppressWarnings("ManualArrayToCollectionCopy")
        public Object java_mutable() {
            final java.util.PriorityQueue<Integer> values = new java.util.PriorityQueue<>(CONTAINER_SIZE);
            for (Integer element : ELEMENTS) {
                values.add(element);
            }
            assert values.size() == CONTAINER_SIZE;
            int aggregate = 0;
            for (; !values.isEmpty(); values.poll()) {
                aggregate ^= values.peek();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        @SuppressWarnings("ManualArrayToCollectionCopy")
        public Object java_blocking_mutable() {
            final java.util.concurrent.PriorityBlockingQueue<Integer> values = new java.util.concurrent.PriorityBlockingQueue<>(CONTAINER_SIZE);
            for (Integer element : ELEMENTS) {
                values.add(element);
            }
            assert values.size() == CONTAINER_SIZE;
            int aggregate = 0;
            for (; !values.isEmpty(); values.poll()) {
                aggregate ^= values.peek();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object scala_mutable() {
            scala.collection.mutable.PriorityQueue<Integer> values = new scala.collection.mutable.PriorityQueue<>(SCALA_ORDERING);
            for (Integer element : ELEMENTS) {
                values = values.$plus$eq(element);
            }
            assert values.size() == CONTAINER_SIZE;
            int aggregate = 0;
            while (!values.isEmpty()) {
                aggregate ^= values.dequeue();
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object scalaz_persistent() {
            scalaz.Heap<Integer> values = scalaz.Heap.Empty$.MODULE$.apply();
            for (Integer element : ELEMENTS) {
                values = values.insert(element, SCALAZ_ORDER);
            }
            assert values.size() == CONTAINER_SIZE;
            int aggregate = 0;
            while (!values.isEmpty()) {
                final scala.Tuple2<Integer, Heap<Integer>> uncons = values.uncons().get();
                aggregate ^= uncons._1;
                values = uncons._2;
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        @SuppressWarnings("ManualArrayToCollectionCopy")
        public Object java_treeset() {
            javaslang.collection.TreeMap<Integer, javaslang.collection.List<Integer>> values = javaslang.collection.TreeMap.empty();
            for (Integer element : ELEMENTS) {
                final javaslang.collection.List<Integer> vs = values.get(element).getOrElse(javaslang.collection.List.empty()).prepend(element);
                values = values.put(element, vs);
            }
            assert values.values().map(Traversable::size).sum().intValue() == CONTAINER_SIZE;
            int aggregate = 0;
            while (!values.isEmpty()) {
                final Tuple2<Integer, javaslang.collection.List<Integer>> min = values.head();
                for (Integer integer : min._2) {
                    aggregate ^= integer;
                }
                values = values.remove(min._1);
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }

        @Benchmark
        public Object slang_persistent() {
            javaslang.collection.PriorityQueue<Integer> values = javaslang.collection.PriorityQueue.empty();
            for (Integer element : ELEMENTS) {
                values = values.enqueue(element);
            }
            assert values.size() == CONTAINER_SIZE;
            int aggregate = 0;
            while (!values.isEmpty()) {
                final Tuple2<Integer, javaslang.collection.PriorityQueue<Integer>> dequeue = values.dequeue();
                aggregate ^= dequeue._1;
                values = dequeue._2;
            }
            assert values.isEmpty() && aggregate == EXPECTED_AGGREGATE;
            return values;
        }
    }
}