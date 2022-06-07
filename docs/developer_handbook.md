# Developer Handbook

This document contains instructions for developers who want to contribute to this project.

## How to extend the Gateway Protocol?

* The gateway protocol is based on GRPC
* The single source of truth is the [`gateway.proto`](../gateway-protocol/src/main/proto/gateway.proto) [Protocol Buffers](https://developers.google.com/protocol-buffers) file
* Source code is generated based on the information in that file
* Make your changes in that file
* Add comments to new fields/messages you added
* Remember to also update the GRPC API documentation https://docs.camunda.io/docs/apis-clients/grpc/

## How to update the `.gocompat.json` file?

* This file is to detect changes to the Go interface
* The comparison is part of the build process
* If changes are deliberate it is necessary to regenerate this file for the build to pass
* This is achieved by running ``cd clients/go && gocompat save ./...` and comitting the changes

