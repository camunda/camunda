# Tasks

Read this file only if the build registers or configures a task, or has task/plugin source under `buildSrc/` or `build-logic/`.

## Avoid DependsOn · `avoid_depends_on` · Medium
When: any `dependsOn` appears.
Detect (det): `dependsOn` where the depended-on task has a `@TaskAction` or `doLast` / `doFirst` — i.e. is not a pure lifecycle task such as `build`, `check`, `assemble`.
Fix: Decide first whether an artifact actually flows between the two tasks. **If it does** — the depended-on task writes something the depender reads — wire output into input (`inputs.file(tasks.named<T>("produce").map { it.outputFile })`) and delete the `dependsOn`. **If nothing flows and the edge is pure ordering**, input wiring is unavailable and is not the answer; pick by what the ordering has to guarantee: fold the first task's work into the second (extract the shared logic into a class or function both call — that is reuse, not duplication) when the follow-up must always happen; `finalizedBy` on the producer when a step must always trail it; `mustRunAfter` only when the ordering matters solely if both tasks are already in the graph. A conclusion of "cannot remove this without changing behavior" means the wrong branch was taken, not that the edge is required.

## Favor `@CacheableTask` and `@DisableCachingByDefault` over `cacheIf(Spec)` and `doNotCacheIf(String, Spec)` · `use_cacheability_annotations` · Medium
When: the build defines a custom task type.
Detect (det): `outputs.cacheIf` or `outputs.doNotCacheIf` anywhere.
Fix: Annotate the task class `@CacheableTask`, or `@DisableCachingByDefault(because = "…")`.

## Group and Describe custom Tasks · `group_describe_tasks` · Recommendation
When: the build registers a task.
Detect (det): a `tasks.register(` / `tasks.create(` block, or a custom task class, with no `group =` / `group` and no `description =` / `description` assignment.
Fix: Set `group` and `description`, in the registration block or the task class constructor.

## Do not call `get()` on a Provider outside a Task action · `avoid_provider_get_outside_task_action` · High
When: the build uses providers or lazy properties.
Detect (det): `.get()`, `.getOrElse(`, `.getOrNull()`, `.isPresent` on a `Provider` / `Property`, or `layout.buildDirectory.get()`, at the top level of a script or inside a task *configuration* block — as opposed to inside `@TaskAction` / `doLast`.
Fix: Chain with `.map { }` instead of calling `.get()` during configuration. Deleting the `.get()` is only half the change — check what receives the value, because handing a raw provider to the wrong API fails silently. APIs typed `Provider`/`Property` accept it (`Directory.file(Provider<CharSequence>)`, `.set(…)`, `ConfigurableFileCollection.from(…)`). APIs typed `Object` / `String` / `Any` do **not** unwrap it — `systemProperty(name, value)`, `environment(…)`, `args(…)`, `extra[…]`, and any string template `"$p"` call `toString()` at execution, so the value silently becomes the literal text `extension 'carLog' property 'logFileName'` and the build still succeeds. For those, resolve inside the task action instead: `doFirst { systemProperty("k", p.get()) }` — `.get()` inside an action is exactly where this practice permits it.

## Don't resolve Configurations before Task Execution · `dont_resolve_configurations_before_task_execution` · High
When: the build passes a configuration to a task.
Detect (det): `.resolve()` on a configuration, or assignment of `configurations.<name>.files` / `.asFileTree` / `.singleFile` to a task property during configuration.
Fix: Declare an `@InputFiles ConfigurableFileCollection` and wire the configuration into it.

## Avoid using eager APIs on File Collections · `avoid_eager_file_collection_apis` · High
When: the build touches file collections or configurations.
Detect (det): `.size`, `.isEmpty()`, `.files`, `.asPath`, `.toList()`, or arithmetic (`+`) on a `Configuration` / `FileCollection` outside a task action.
Fix: Pass the collection through unresolved; inspect it inside the task action.

## Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directories · `default_path_sensitivities` · Medium
When: the build defines a custom task type with file or directory inputs.
Detect (det): `PathSensitivity.ABSOLUTE`; `PathSensitivity.NAME_ONLY` on an input; or an `@InputFile` / `@InputFiles` / `@InputDirectory` with no `@PathSensitive` at all — the default is `ABSOLUTE`.
Fix: Add `@PathSensitive(NONE)` to `@InputFile`, `RELATIVE` to `@InputDirectory`.

## Use unique output files and directories · `use_unique_output_files_and_directories` · Medium
When: two or more tasks declare outputs.
Detect (det): two task registrations whose `@OutputDirectory` / `outputs.dir` resolve to the same path, e.g. both using `layout.buildDirectory.dir("greetings")`.
Fix: Give each task a distinct output path, or use `@OutputFile` with distinct names.

## Don't hardcode Task names unless they are documented as Public API · `dont_hardcode_task_names` · Recommendation
When: the build looks tasks up by name.
Detect (heur): `tasks.named("...")` / `tasks.getByName("...")` with a **string literal** task name, e.g. `"generatePomFileForMavenPublication"`. Two things are *not* violations and must not be flagged: by-**type** lookups such as `tasks.withType<JavaCompile>()`, which the documentation ranks *above* configuring by name, so flagging them inverts the practice; and a name supplied as a documented public constant, e.g. `tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME)`. Heuristic because deciding whether a literal is documented public API needs judgment — report the literal and say why it looks internal.
Fix: Configure through the plugin's DSL; failing that, use the task provider, not a literal.

## Don't access a `Project` instance during Task Execution · `dont_access_project_instance_inside_task` · High
When: the build defines a custom task type or uses `doLast` / `doFirst`.
Detect (det): `project.` inside a `@TaskAction` method, or inside a `doLast {` / `doFirst {` block — e.g. `project.version`, `project.layout`, `project.file(...)`.
Fix: Declare the value as an `@Input` property, set it at configuration time, read it in the action.

## Wire lazy task outputs using `map` and `flatMap` · `map_versus_flatmap` · Medium
When: one task consumes another's output.
Detect (det): `.get()` inside a `map {` / `flatMap {` closure; a bare `provider { someTask.get().output.get() }`; or `map { it.property.get() }`.
Fix: Use `flatMap` to reach a `Provider`-typed task output, `map` to transform a value.

## Favor collection property types over a `Property` holding a collection · `favor_collection_properties` · Medium
When: the build defines a custom task type, extension, or plugin with properties.
Detect (det): `Property<Collection<…>>`, `Property<Iterable<…>>`, `Property<List<…>>`, `Property<Set<…>>` or `Property<Map<…>>`; or a plain `List` / `Set` / `Map` field serving as a task input. Gradle rejects `Property<List<T>>` outright, so the surviving forms are the `Collection` / `Iterable` / plain-field spellings.
Fix: Replace with `ListProperty` / `SetProperty` / `MapProperty` and contribute via `add` / `addAll` / `put`.
