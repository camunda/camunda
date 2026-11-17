# Spring Conventions

Conventions for Spring-based modules in this monorepo (e.g., `dist/`, `clients/`,
`gateways/`, `authentication/`).

## Avoid `@ConditionalOnBean` / `@ConditionalOnMissingBean` on `@Configuration` classes

Per [Spring's documentation](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.condition-annotations),
these annotations should be limited to **auto-configuration** classes and not used on
regular `@Configuration` classes.

On a regular `@Configuration`, the result depends on bean-loading order. Because the
order is not guaranteed, an unrelated change elsewhere can silently flip which branch
applies. The "wrong" branch is often a no-op, so startup still succeeds and the bug only
surfaces later, when the feature is actually exercised — and the configuration class
itself looks untouched, making it hard to trace.

### Do this instead

Inject an `ObjectProvider<T>` and resolve the bean lazily:

```java
@Configuration
class MyConfiguration {

  @Bean
  MyService myService(final ObjectProvider<OptionalDependency> dependency) {
    final var dep = dependency.getIfAvailable();
    if (dep == null) {
      return MyService.noop(); // or: throw, return a default, log and degrade — pick per use case
    }
    return new MyService(dep);
  }
}
```

`ObjectProvider#getIfAvailable()` is evaluated after all bean definitions are
registered, so bean-load order does not affect the outcome.

> Note: these annotations exist for use on auto-configuration classes (registered via
> `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`),
> where Spring loads them after user beans. Unless you are writing an auto-configuration,
> use `ObjectProvider`.

