# Name Version Catalog Entries Appropriately
`name_version_catalog_entries`

**Rule:** Name catalog entries from the module coordinates: 1-3 dash-separated segments, dropping the TLD segment, converting artifact dashes to camelCase, and avoiding redundancy.

---

- **Fix:** Rename per the published mapping. The middle column is the catalog key;
  the right column is how a build script must then refer to it, because Gradle
  turns each `-` in a key into a dot in the accessor (see `use_version_catalogs`):

  | Coordinates | Catalog key | Accessor |
  |---|---|---|
  | `org.apache.commons:commons-lang3` | `commons-lang3` | `libs.commons.lang3` |
  | `com.fasterxml.jackson.core:jackson-databind` | `jackson-databind` | `libs.jackson.databind` |
  | `com.fasterxml.jackson.dataformat:jackson-dataformat-csv` | `jackson-dataformatCsv` | `libs.jackson.dataformatCsv` |
  | `org.slf4j:slf4j-api` | `slf4j-api` | `libs.slf4j.api` |
  | `org.spockframework:spock-core` | `spock-core` | `libs.spock.core` |
  | `org.junit.platform:junit-platform-launcher` | `junit-platform-launcher` | `libs.junit.platform.launcher` |

  Note the camelCase rule applies to the *key* only — it never changes how the
  accessor is spelled. Renaming a key means updating every accessor that
  referenced it; re-run the build afterwards.
