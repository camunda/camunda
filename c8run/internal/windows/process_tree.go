//go:build windows

// process_tree.go
package windows

import (
	"fmt"
	"syscall"
	"unsafe"

	"github.com/rs/zerolog/log"
)

func processTree(pid int) []int {
	rootPid := uint32(pid)
	tree, err := getTreePids(rootPid)
	if err != nil {
		log.Debug().Err(err).Uint32("pid", rootPid).Msg("Failed to resolve Windows process tree")
	}

	processList := make([]int, 0, len(tree))
	for _, pid := range tree {
		processList = append(processList, int(pid))
	}
	return processList
}

// getTreePids will return a list of pids that represent the tree of process pids originating from the specified one.
// (they are ordered: [parent, 1 gen child, 2 gen child, ...])
func getTreePids(rootPid uint32) ([]uint32, error) {
	// https://docs.microsoft.com/en-us/windows/win32/api/tlhelp32/ns-tlhelp32-processentry32
	procEntry := syscall.ProcessEntry32{}

	snapshot, err := syscall.CreateToolhelp32Snapshot(uint32(syscall.TH32CS_SNAPPROCESS), 0)
	if err != nil {
		return nil, err
	}
	defer syscall.CloseHandle(snapshot)

	procEntry.Size = uint32(unsafe.Sizeof(procEntry))

	err = syscall.Process32First(snapshot, &procEntry)
	if err != nil {
		return nil, err
	}

	entries := make([]processEntry, 0, 128)
	for {
		entries = append(entries, processEntry{
			pid:  procEntry.ProcessID,
			ppid: procEntry.ParentProcessID,
		})

		err = syscall.Process32Next(snapshot, &procEntry)
		if err != nil {
			break
		}
	}

	return buildProcessTree(rootPid, entries)
}

type processEntry struct {
	pid  uint32
	ppid uint32
}

func buildProcessTree(rootPid uint32, entries []processEntry) ([]uint32, error) {
	parentLayer := []uint32{rootPid}
	treePids := append([]uint32(nil), parentLayer...)
	foundRootPid := false

	for {
		var childLayer []uint32
		for _, entry := range entries {
			if entry.pid == rootPid {
				foundRootPid = true
			}

			if contains(parentLayer, entry.ppid) && !contains(treePids, entry.pid) {
				childLayer = append(childLayer, entry.pid)
			}
		}

		if len(childLayer) == 0 {
			if !foundRootPid && len(treePids) == 1 {
				return nil, fmt.Errorf("getTreePids: specified rootPid not found")
			}
			return treePids, nil
		}

		treePids = append(treePids, childLayer...)
		parentLayer = childLayer
	}
}

func contains(list []uint32, e uint32) bool {
	for _, l := range list {
		if l == e {
			return true
		}
	}
	return false
}
