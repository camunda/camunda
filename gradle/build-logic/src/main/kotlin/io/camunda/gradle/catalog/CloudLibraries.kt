package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** Cloud provider SDKs and their credential and storage clients: Azure, Google Cloud and AWS. */
internal fun VersionCatalogBuilder.catalogCloudLibraries() {
  library("com-azure-azure-sdk-bom", "com.azure", "azure-sdk-bom").versionRef("azure-sdk")
  library("com-azure-azure-core", "com.azure", "azure-core").withoutVersion()
  library("com-azure-azure-identity", "com.azure", "azure-identity").withoutVersion()
  library("com-azure-azure-storage-blob", "com.azure", "azure-storage-blob").withoutVersion()
  library("com-azure-azure-storage-blob-batch", "com.azure", "azure-storage-blob-batch")
    .withoutVersion()
  library("com-azure-azure-storage-common", "com.azure", "azure-storage-common")
    .withoutVersion()
  library("com-google-api-api-common", "com.google.api", "api-common").withoutVersion()
  library("com-google-api-gax", "com.google.api", "gax").withoutVersion()
  library("com-google-api-gax-grpc", "com.google.api", "gax-grpc").withoutVersion()
  library(
      "com-google-api-grpc-proto-google-cloud-secretmanager-v1",
      "com.google.api.grpc",
      "proto-google-cloud-secretmanager-v1",
    )
    .withoutVersion()
  library(
      "com-google-api-grpc-proto-google-common-protos",
      "com.google.api.grpc",
      "proto-google-common-protos",
    )
    .versionRef("com-google-api-grpc-proto-google-common-protos")
  library("com-google-cloud-google-cloud-core", "com.google.cloud", "google-cloud-core")
    .withoutVersion()
  library("com-google-cloud-google-cloud-storage", "com.google.cloud", "google-cloud-storage")
    .withoutVersion()
  library(
      "com-google-cloud-google-cloud-secretmanager",
      "com.google.cloud",
      "google-cloud-secretmanager",
    )
    .withoutVersion()
  library("com-google-cloud-libraries-bom", "com.google.cloud", "libraries-bom")
    .versionRef("google-sdk")
  library("software-amazon-awssdk-apache-client", "software.amazon.awssdk", "apache-client")
    .withoutVersion()
  library("software-amazon-awssdk-auth", "software.amazon.awssdk", "auth").withoutVersion()
  library("software-amazon-awssdk-aws-core", "software.amazon.awssdk", "aws-core")
    .withoutVersion()
  library("software-amazon-awssdk-aws-crt-client", "software.amazon.awssdk", "aws-crt-client")
    .withoutVersion()
  library("software-amazon-awssdk-http-auth-aws", "software.amazon.awssdk", "http-auth-aws")
    .withoutVersion()
  library("software-amazon-awssdk-http-auth-spi", "software.amazon.awssdk", "http-auth-spi")
    .withoutVersion()
  library("software-amazon-awssdk-http-client-spi", "software.amazon.awssdk", "http-client-spi")
    .withoutVersion()
  library(
      "software-amazon-awssdk-netty-nio-client",
      "software.amazon.awssdk",
      "netty-nio-client",
    )
    .withoutVersion()
  library("software-amazon-awssdk-regions", "software.amazon.awssdk", "regions")
    .withoutVersion()
  library("software-amazon-awssdk-retries", "software.amazon.awssdk", "retries")
    .withoutVersion()
  library("software-amazon-awssdk-retries-spi", "software.amazon.awssdk", "retries-spi")
    .withoutVersion()
  library("software-amazon-awssdk-rds", "software.amazon.awssdk", "rds").withoutVersion()
  library("software-amazon-awssdk-secretsmanager", "software.amazon.awssdk", "secretsmanager")
    .withoutVersion()
  library("software-amazon-awssdk-s3", "software.amazon.awssdk", "s3").withoutVersion()
  library("software-amazon-awssdk-sdk-core", "software.amazon.awssdk", "sdk-core")
    .withoutVersion()
  library("software-amazon-awssdk-sts", "software.amazon.awssdk", "sts").withoutVersion()
  library("software-amazon-awssdk-bom", "software.amazon.awssdk", "bom").versionRef("awssdk")
}
