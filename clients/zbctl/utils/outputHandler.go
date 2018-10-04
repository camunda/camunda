package utils

import (
	"encoding/json"
	"os"
)

type OutputWriter struct {
	buff []byte
}

func (out *OutputWriter) Serialize(payload interface{}) *OutputWriter {
	b, err := json.MarshalIndent(payload, "", "  ")
	CheckOrExit(err, ExitCodeGeneralError, nil)
	out.buff = b
	return out
}

func (out *OutputWriter) Write(p []byte) (n int, err error) {
	return os.Stdout.Write(p)
}

func (out *OutputWriter) Flush() {
	out.buff = append(out.buff, byte('\n'))
	out.Write(out.buff)
	out.buff = make([]byte, 0)
}

func NewOutputWriter() *OutputWriter {
	return &OutputWriter{
		buff: make([]byte, 0),
	}
}
