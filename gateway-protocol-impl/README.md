# Zeebe Gateway Protocol Implementation

Zeebe client grpc protobuf protocol.


## Go Code Generation

### Prerequisite
To generate the go grpc stubs you need the following tools:

1. Go >= 1.7 (https://golang.org/dl/)
1. The go protoc generator
    ```
    go get -u github.com/golang/protobuf/protoc-gen-go
    ```
1. Maven >= 3.3.1 (https://maven.apache.org/download.cgi)


### Generate Stubs

To generate the go stubs run

```
mvn generate-sources -P golang
```

the stubs will be generated in the `clients/go/pb` directory.

