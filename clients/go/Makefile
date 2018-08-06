.PHONY: install-deps
install-deps:
	go get -u github.com/golang/dep/cmd/dep
	go get github.com/onsi/ginkgo/ginkgo
	go get github.com/onsi/gomega/...
	dep ensure

.PHONY: test
test:
	cd tests/integration/ && ginkgo && cd ../..
