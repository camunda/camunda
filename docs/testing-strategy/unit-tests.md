# Unit Tests

> Back to [Testing Strategy](./README.md)

## Definition

A unit test verifies a single class or function in complete isolation. All collaborators are mocked or stubbed. No I/O, no network, no database, no file system, no Docker containers.

## Rules

1. **One assertion focus per test** — test a single behavior, not multiple scenarios
2. **Given/When/Then structure** — every test must use `// given`, `// when`, `// then` comments
3. **No shared mutable state** — use `@BeforeEach` for per-test setup, never static mutable fields
4. **Mock external dependencies** — use `@ExtendWith(MockitoExtension.class)` and `@Mock` / `@InjectMocks`
5. **Fast** — individual unit tests should execute in < 100ms. If a "unit test" needs > 1 second, it's an integration test
6. **No Spring context** — unit tests must never use `@SpringBootTest`, `@WebMvcTest`, or any Spring test annotation that loads an application context

## Gold Standard Example

From `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/batchoperation/scheduler/BatchOperationPageProcessorTest.java`:

```java
class BatchOperationPageProcessorTest {

  private static final long BATCH_OPERATION_KEY = 123L;
  private static final int CHUNK_SIZE = 2;

  private BatchOperationPageProcessor processor;
  private TaskResultBuilder mockTaskResultBuilder;

  @BeforeEach
  void setUp() {
    processor = new BatchOperationPageProcessor(CHUNK_SIZE);
    mockTaskResultBuilder = mock(TaskResultBuilder.class);
  }

  @Test
  void shouldProcessPageWithSingleChunk() {
    // given
    final var item1 = new Item(100L, 200L, null);
    final var item2 = new Item(101L, 201L, null);
    final var page = new ItemPage(List.of(item1, item2), "cursor123", 2L, false);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    final var result = processor.processPage(BATCH_OPERATION_KEY, page, mockTaskResultBuilder);

    // then
    assertThat(result.chunksAppended()).isTrue();
    assertThat(result.endCursor()).isEqualTo("cursor123");
    assertThat(result.itemsProcessed()).isEqualTo(2);
    assertThat(result.isLastPage()).isFalse();
  }
}
```

**Why this is the standard:**
- Clear naming: `shouldProcessPageWithSingleChunk`
- Constants are named: `BATCH_OPERATION_KEY`, `CHUNK_SIZE`
- `@BeforeEach` creates fresh instances — no shared state
- Single behavior per test
- Given/when/then structure
- AssertJ assertions
- No Spring context, no I/O
