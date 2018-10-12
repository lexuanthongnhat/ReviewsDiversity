package edu.ucr.cs.dblab.nle020.utils;

/**
 * Trio Function that accepts 3 arguments
 */
@FunctionalInterface
public interface TriFunction<A, B, C, R> {
  R apply(A a, B b, C c);
}
