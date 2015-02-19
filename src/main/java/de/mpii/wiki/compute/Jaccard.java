package de.mpii.wiki.compute;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mpii.wiki.common.Utils;


public class Jaccard {
  public static double compute(List<String> list1, List<String> list2) {
    Set<String> set1 = new HashSet<>(Utils.verifyList(list1));
    Set<String> set2 = new HashSet<>(Utils.verifyList(list2));

    int sizeCurrentSet = set1.size();
    set1.retainAll(set2);
    set2.removeAll(set1);

    int union = sizeCurrentSet + set2.size();
    int intersection = set1.size();
    return ((double)intersection)/(double)union;
  }
}
