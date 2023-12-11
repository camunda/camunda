# Zeebe Gateway Protocol Implementation

Zeebe client grpc protobuf protocol.

## Go Code Generation

### Prerequisite

To generate the go grpc stubs you need the following tools:

1. Go >= 1.19 (https://golang.org/dl/)
2. The go & go-grpc protoc generators

   ```
   go install google.golang.org/protobuf/cmd/protoc-gen-go
   go install google.golang.org/grpc/cmd/protoc-gen-go-grpc
   ```
3. Maven >= 3.3.1 (https://maven.apache.org/download.cgi)

### Generate Stubs

To generate the go stubs run

```
mvn generate-sources -P golang
```

the stubs will be generated in the `clients/go/pkg/pb` directory.

