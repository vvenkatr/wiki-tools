package de.mpii.wiki.compute;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import de.mpii.wiki.compute.Jaccard;


public class JaccardTest {

  @Test
  public void testJaccard() {
    List<String> l1 = Arrays.asList("{{refer}}* [[Retusin (flavonol)]] (or quercetin-3,7,3',4'-tetramethyl ether, CAS number 1245-15-4)* [[Retusin (isoflavone)]] (or 7,8-dihydroxy-4′-methoxyisoflavone, CAS number 37816-19-6)==External links==* [http://kanaya.naist.jp/knapsack_jsp/result.jsp?sname=metabolite&word=retusin Retusin on kanaya.naist.jp]{{disambiguation}}".split(" "));
    List<String> l2 = Arrays.asList("'''Retusin''' may refer to:* [[Retusin (flavonol)]] (or quercetin-3,7,3',4'-tetramethyl ether, CAS number 1245-15-4)* [[Retusin (isoflavone)]] (or 7,8-dihydroxy-4′-methoxyisoflavone, CAS number 37816-19-6)==External links==* [http://kanaya.naist.jp/knapsack_jsp/result.jsp?sname=metabolite&word=retusin Retusin on kanaya.naist.jp]{{Chemistry disambiguation}}".split(" "));
    System.out.println(Jaccard.compute(l1, l2));
  }
}
