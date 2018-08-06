.PHONY: install-test
install-test:
	go get github.com/onsi/ginkgo/ginkgo
	go get github.com/onsi/gomega/...

.PHONY: test
test:
	pushd tests/integration/ && ginkgo && popd
