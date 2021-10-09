package io.atomix.cluster.messaging.grpc;

public final class GrpcManagedServices {
  private final GrpcManagedMessagingService messagingService;
  private final GrpcManagedUnicastService unicastService;

  public GrpcManagedServices(
      final GrpcManagedMessagingService messagingService,
      final GrpcManagedUnicastService unicastService) {
    this.messagingService = messagingService;
    this.unicastService = unicastService;
  }

  public GrpcManagedMessagingService getMessagingService() {
    return messagingService;
  }

  public GrpcManagedUnicastService getUnicastService() {
    return unicastService;
  }
}
