# Mapping Wikipedia URLs Between Revisions

Wikipedia URLs change over time and are thus not a good choice as stable identifier. This is especially problematic when the URLs are used as identifier in NED frameworks, thus we need to have a mapping between different timepoints.

## Reasons for URL Changes

There are many reason for URL changes:
 * Guidelines change: Wikipedia editors jointly decide on a naming scheme that enforces some consistency (http://en.wikipedia.org/wiki/Wikipedia:Proposed_naming_conventions_and_guidelines) - usually the page id stays and the original name becomes a redirect.
 * New entities with existing names appear: entity pages become disambiguation pages, the actual entities get a "()" qualifier attached
 * Editors decide on a per-article basis that the name is not a good fit
 * ... please research more reasons

## Goal

The goal is to create a tool that can use two single Wikipedia dumps of the appropriate timestamps [1] to create the mappings.

Input:
 * Two Wikipedia revision dumps [1]
 	* Source dump - Input URLs.
 	* Target dump - Provides output URLs corresponding to the URLs from source dump. 
Output:
 * List of URLs corresponding to the input URLs.
 	* Each entry has source URL and corresponding target URL along with mapping information


## Mapping Features

There are several features to use when doing the mapping:
 * Internal Wikipedia IDs: The _should_ stay stable when using the rename functionality, but not everyone does that
 * Redirects: If a URL becomes a redirect in later versions, this is a good indicator of the new name
 * Page features for actual comparison. A large number of possibilities is there, a good starting point are the
   * outgoing links on pages - if the links have a high jaccard similarity this is probably a good choice. Useful when deciding a entity-become-disambiguation page where a few candidates are already given.


## Examples

Input:
 * Source Dump: enwiki-20100817-pages-articles.xml
 * Target Dump: enwiki-20140811-pages-articles.xml

Output:
 * A list of entries of format:
 	
 	SOURCE_URL		TARGET_URL		MAP_TYPE	SOURCE_PAGE_TEXT(In evaluation mode)	TARGET_PAGE_TEXT(In evaluation mode)
 	
 	where MAP_TYPE can be:
 		* REDIRECT	(__R__)  - The target URL is obtained via redirect page
 		* REDIRECT_CYCLE (__RC__) - The target URL redirects to itself or to entry that redirects back to the URL.
 		* DISAMBIGUATION (__D__)  - The mapping is obtained by computing the similarity of source URL with all disambiguate pages the target points to.
 		* UNCHANGED (__UC__)	- Source page is same as target.
 		* UPDATED (__UP__)		- Source page id is same as target id but title information has been changed
 		* DELETED (__DL__)		- The page entry in source dump has been removed in the target.
 		
Sample:
	
	... (other mappings) ...
	http://en.wikipedia.org/wiki/People%27s_Republic_of_China	http://en.wikipedia.org/wiki/China	__R__
	...
	
	
## API Usage

The main purpose of this tool is to generate a file containing all required mappings. In this regard, a simple script has been provided:

```
./scripts/map_wiki_urls.sh --source <OLD_DUMP_FILE_PATH> --target <NEW_DUMP_FILE_PATH> --output <FILE>
```

For use within another application, the main class WikiMapper provides a public method map() which returns the result as Map<String, String>.

```
Map<String, String> results = WikiMapper.map(sourceDump, targetDump);
```

## Memory Requirements

Will be updated

[1] http://dumps.wikimedia.org/enwiki/20140811/enwiki-20140811-pages-articles.xml (1 File)
