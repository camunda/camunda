# Camunda DMN Parallel Worker Example

This example demonstrates how to build a job worker using the Camunda Spring Boot SDK that:

1. Fetches a list of JSON objects from a process variable
2. Executes DMN decisions for each item in parallel using **virtual threads**
3. Uses **Java 21 structured concurrency** for coordinated parallel execution
4. Returns aggregated results back to the process

## Key Features

### Virtual Threads (Java 21)
Virtual threads are lightweight threads that allow for massive concurrency without the overhead of platform threads. This makes it ideal for I/O-bound and CPU-bound tasks like DMN evaluation.

### Structured Concurrency (Java 21)
The `StructuredTaskScope` API provides a way to structure concurrent operations as a single unit of work, with proper error handling and resource management.

### DMN Engine Integration
Uses the same DMN Java library (`zeebe-dmn`) that powers the Zeebe engine internally, ensuring consistency with production behavior.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ Process Instance                                         │
│  ┌──────────┐         ┌──────────────┐       ┌────────┐ │
│  │  Start   │────────▶│Process Orders│──────▶│  End   │ │
│  └──────────┘         └──────────────┘       └────────┘ │
│                              │                            │
│                              │ Job: process-orders        │
└──────────────────────────────┼────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ ParallelDmnJobWorker │
                    └──────────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    │ Structured Task     │
                    │ Scope (Virtual      │
                    │ Threads)            │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
    ┌─────────┐          ┌─────────┐         ┌─────────┐
    │ Order 1 │          │ Order 2 │   ...   │ Order N │
    │   DMN   │          │   DMN   │         │   DMN   │
    └─────────┘          └─────────┘         └─────────┘
```

## Components

### 1. `ParallelDmnJobWorker`
The main job worker that:
- Accepts a `orders` variable containing JSON array of order objects
- Uses `StructuredTaskScope.ShutdownOnFailure()` for parallel execution
- Forks a virtual thread for each order to evaluate the DMN decision
- Waits for all subtasks to complete with `scope.join()`
- Aggregates results and returns them as process variables

### 2. `DmnConfiguration`
Spring configuration that:
- Creates the `DecisionEngine` instance
- Parses the DMN file at startup
- Provides the parsed DRG as a Spring bean

### 3. DMN Decision: `discount-calculation.dmn`
A decision table that calculates discount percentage based on:
- `orderAmount`: The total order amount
- `customerType`: Either "PREMIUM" or "REGULAR"

Rules:
- Premium customers: 20% for orders ≥ $1000, 15% for orders ≥ $500
- Regular customers: 10% for orders ≥ $1000, 5% for orders ≥ $500
- Default: 0% discount

## Building

```bash
./mvnw clean install -pl testing/camunda-dmn-parallel-worker-example -am
```

## Running Tests

```bash
./mvnw verify -pl testing/camunda-dmn-parallel-worker-example
```

## Test Cases

The `ParallelDmnJobWorkerTest` includes:

1. **shouldProcessMultipleOrdersInParallel**: Tests processing 5 orders with different amounts and customer types
2. **shouldHandleSingleOrder**: Verifies single order processing
3. **shouldHandleEmptyOrderList**: Tests edge case of empty order list

## Usage Example

```java
@Component
public class ParallelDmnJobWorker {
  
  @JobWorker(type = "process-orders")
  public Map<String, Object> processOrders(
      JobClient client, 
      ActivatedJob job, 
      @Variable("orders") String ordersJson) throws Exception {
    
    List<Map<String, Object>> orders = objectMapper.readValue(ordersJson, ...);
    
    // Process orders in parallel using structured concurrency
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
      List<Subtask<Map<String, Object>>> subtasks = new ArrayList<>();
      
      for (Map<String, Object> order : orders) {
        Subtask<Map<String, Object>> subtask = scope.fork(() -> processOrder(order));
        subtasks.add(subtask);
      }
      
      scope.join();      // Wait for all tasks
      scope.throwIfFailed(); // Propagate failures
      
      // Collect results
      List<Map<String, Object>> results = subtasks.stream()
          .map(Subtask::get)
          .toList();
          
      return Map.of("orderResults", results);
    }
  }
}
```

## Benefits of This Approach

1. **Scalability**: Virtual threads can handle thousands of concurrent DMN evaluations efficiently
2. **Simplicity**: Structured concurrency provides clean, readable code compared to CompletableFuture
3. **Reliability**: Automatic cleanup and error propagation via StructuredTaskScope
4. **Performance**: Parallel execution significantly reduces total processing time
5. **Consistency**: Uses the same DMN engine as Zeebe, ensuring identical evaluation behavior

## Requirements

- Java 21 or higher
- Maven 3.x
- Spring Boot 3.x
- Camunda 8.9.0 or higher

## License

Apache License 2.0
