# Use UTF-8 File Encoding
`use_utf8_encoding`

**Rule:** Set UTF-8 as the default file encoding so behaviour is consistent across platforms and does not defeat caching through a platform-dependent default.

---

- **Fix:** In the root `gradle.properties`: `org.gradle.jvmargs=-Dfile.encoding=UTF-8` (append to any existing value rather than replacing it).
