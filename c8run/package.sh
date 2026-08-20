#!/bin/bash

go build -o packager ./cmd/packager/main.go
./packager package
