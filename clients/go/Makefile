.PHONY: install-deps
install-deps:
	go get -u github.com/golang/dep/cmd/dep
	dep ensure

.PHONY: test
test:
	go test -v ./pkg/commands/
	go test -v ./pkg/entities/
	go test -v ./pkg/worker/
	go test -v ./pkg/zbc/
	go test -v ./internal/utils/
	go test -v ./test/
