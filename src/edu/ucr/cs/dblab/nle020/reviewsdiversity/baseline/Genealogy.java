package edu.ucr.cs.dblab.nle020.reviewsdiversity.baseline;

import edu.ucr.cs.dblab.nle020.reviewsdiversity.units.ConceptSentimentPair;

import java.util.*;

/**
 * Genealogy book stores ancestor-successor relationships of medical concepts
 * in a set of concept-sentiment pairs
 */
public class Genealogy {
  private Map<String, Set<String>> ancestorToSuccessors = new HashMap<>();
  private Map<String, Set<String>> successorToAncestors = new HashMap<>();
  private Set<String> concepts = new HashSet<>();

  Genealogy(Collection<ConceptSentimentPair> pairs) {
    Map<String, Set<String>> cuiToDeweys = new HashMap<>();
    for (ConceptSentimentPair pair : pairs) {
      String cui = pair.getCui();
      concepts.add(cui);
      if (!cuiToDeweys.containsKey(cui))
        cuiToDeweys.put(cui, pair.getDeweys());
      if (!ancestorToSuccessors.containsKey(cui))
        ancestorToSuccessors.put(cui, new HashSet<>());
      if (!successorToAncestors.containsKey(cui))
        successorToAncestors.put(cui, new HashSet<>());
    }

    for (String ancestor : concepts) {
      for (String successor : concepts) {
        if (ancestor.equalsIgnoreCase(successor))
          continue;

        boolean done = false;
        for (String ancestorDewey : cuiToDeweys.get(ancestor)) {
          for (String successorDewey : cuiToDeweys.get(successor)) {
            if (is_successor(ancestorDewey, successorDewey)) {
              ancestorToSuccessors.get(ancestor).add(successor);
              successorToAncestors.get(successor).add(ancestor);
              done = true;
              break;
            }
          }
          if (done)
            break;
        }
      }
    }
  }

  /**
   * Check if childDewey is actually a successor of ancestorDewey
   * Note: if two Dewey are the same, return false (a dewey is not its own child)
   */
  private static boolean is_successor(String ancestorDewey, String childDewey) {
    if (ancestorDewey.startsWith(childDewey)) {
      return ancestorDewey.length() > childDewey.length() &&
          ancestorDewey.charAt(childDewey.length()) == '.';
    }
    return false;
  }

  Set<String> getSuccessors(String cui) {
    return ancestorToSuccessors.get(cui);
  }

  public Set<String> getConcepts() {
    return this.concepts;
  }
}
