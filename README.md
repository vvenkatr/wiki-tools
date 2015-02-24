# Mapping Wikipedia URLs Between Revisions

Wikipedia URLs change over time and are thus not a good choice as stable identifier. This is especially problematic when the URLs are used as identifier in NED frameworks, thus we need to have a mapping between different timepoints.

## Reasons for URL Changes

There are many reason for URL changes:
 * Guidelines change: Wikipedia editors jointly decide on a naming scheme that enforces some consistency (http://en.wikipedia.org/wiki/Wikipedia:Proposed_naming_conventions_and_guidelines) - usually the page id stays and the original name becomes a redirect.
 * New entities with existing names appear: entity pages become disambiguation pages, the actual entities get a "()" qualifier attached
 * Editors decide on a per-article basis that the name is not a good fit

## Goal

The goal is to create a tool that can use two single Wikipedia dumps of the appropriate timestamps [1] to create the mappings.

Input:
 * Two Wikipedia pages-articles.xml dumps at different points in time
 	* Source dump - Input URLs.
 	* Target dump - Provides output URLs corresponding to the URLs from source dump. 
Output:
 * List of URLs corresponding to the input URLs.
 	* Each entry has source URL and corresponding target URL along with mapping information


## Mapping Features

There are several features to use when doing the mapping:
 * Updated Title: The title wiki page with particular id is renamed in the later version. 
 * Redirects: If a URL becomes a redirect in later versions, this is a good indicator of the new name.
 * Page features for actual comparison. A large number of possibilities is there, a good starting point are the
   * outgoing links on pages - if the links have a high jaccard similarity this is probably a good choice. Useful when deciding a entity-become-disambiguation page where a few candidates are already given.


## Examples

Input:
 * Source Dump: enwiki-20100817-pages-articles.xml
 * Target Dump: enwiki-20140811-pages-articles.xml

Output:
 * A tab-separated UTF-8 text file where each line corresponds to one mapping with additional information:
 	
 SOURCE-URL	TARGET-URL	MAP-TYPE	SRC-TEXT(eval mode)	TGT-TEXT(eval mode)
 	
 where MAP_TYPE can be:
 - REDIRECT	(__R__)  - The target URL is obtained via redirect page
 - REDIRECT_CYCLE (__RC__) - The target URL redirects to itself or to entry that redirects back to the URL.
 - DISAMBIGUATION (__D__)  - The mapping is obtained by computing the similarity of source URL with all disambiguate pages the target points to.
 - UNCHANGED (__UC__)	- Source page is same as target.
 - UPDATED (__UP__)		- Source page id is same as target id but title information has been changed
 - DELETED (__DL__)		- The page entry in source dump has been removed in the target.
 		
Sample:
	
	... (other mappings) ...
	http://en.wikipedia.org/wiki/People%27s_Republic_of_China	http://en.wikipedia.org/wiki/China	__R__
	...

	
## Usage

The project can be compiled using:

```
mvn compile
```

The main purpose of this tool is to generate a file containing all required mappings. In this regard, a simple script has been provided:

```
./scripts/map_wiki_urls.sh --source <OLD_DUMP_FILE_PATH> --target <NEW_DUMP_FILE_PATH> --output <FILE>
```

For use within another application, the main class WikiMapper provides a public method map() which returns the result as Map<String, String>.

```
Map<String, String> results = WikiMapper.map(sourceDump, targetDump);
```

## Quality

To estimate how well the disambiguation heuristic works, we evaluated it in the following setup.

1. The tool is executed in the eval mode(Additional page texts are written for disambiguation entries):

```
./scripts/map_wiki_urls.sh --source <OLD_DUMP_FILE_PATH> --target <NEW_DUMP_FILE_PATH> --output <FILE> --evaluate
``` 
2. Around 1000 disambiguation entries are randomly selected from the result and are verified manually by comparing the source text and target text. A simple web interface was developed to support the evaluation process.
3. Correctness value was finally computed based on the number of correctly mapped disambiguation entries. (Some mappings whose source url itself is disambiguation page which were not filtered due use of old marker texts are ignored while computing the correctness)

## Memory Requirements

The maximum memory limit reached during the execution was 55GB. During execution, both target and source dump are scanned once and details are stored in memory. Under normal execution, for target, page id-title, page title-id and id-list of page links maps are stored. For evaluation mode, require main memory increases due to additional text storage for comparison.
