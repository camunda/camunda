package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import io.camunda.zeebe.gateway.rest.util.RetryOperation;
import io.camunda.zeebe.gateway.rest.util.SpringContextHolder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessCacheProvider {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessCacheProvider.class);
  private static final int RETRY_COUNT = 5;

  public static CompletableFuture<ProcessCache> tryFetchProcessCacheBean() {
    final CompletableFuture<ProcessCache> future = new CompletableFuture<>();
    tryFetchingBean(future);
    return future;
  }

  // need to wait and retry as transitions steps starts before the SpringContextHolder gets its
  // context set
  private static void tryFetchingBean(final CompletableFuture<ProcessCache> future) {
    try {
      RetryOperation.newBuilder()
          .noOfRetry(RETRY_COUNT)
          .retryOn(NullPointerException.class)
          .delayInterval(1, TimeUnit.SECONDS)
          .message("Fetching ProcessCache bean")
          .retryConsumer(
              () -> {
                LOG.info("Fetching ProcessCache ...");
                final ProcessCache ProcessCache = SpringContextHolder.getBean(ProcessCache.class);
                future.complete(ProcessCache); // Successfully retrieved the bean
                return true;
              })
          .build()
          .retry();
    } catch (final Exception e) {
      LOG.error("Failed to get the bean", e); // TODO
      future.completeExceptionally(e);
    }
  }
}
