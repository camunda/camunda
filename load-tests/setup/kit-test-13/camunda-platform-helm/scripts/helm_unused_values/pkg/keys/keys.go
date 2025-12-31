package keys

type KeyUsage struct {
	Key         string
	IsUsed      bool
	UsageType   string // "direct", "pattern"
	Locations   []string
	ParentKey   string
	PatternName string
	ChildKeys   []string
}
