package archive

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
)

const OpenFlagsForWriting = os.O_RDWR | os.O_CREATE | os.O_TRUNC
const ReadWriteMode = 0755

func DownloadFile(filepath string, url string, authToken string) error {
	// if the file already exists locally, don't download a new copy
	_, err := os.Stat(filepath)
	if !errors.Is(err, os.ErrNotExist) {
		return nil
	}

	out, err := os.Create(filepath)
	if err != nil {
		return fmt.Errorf("DownloadFile: failed to open file: %s\n%w\n%s", filepath, err, debug.Stack())
	}
	defer out.Close()

	client := &http.Client{}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return fmt.Errorf("DownloadFile: failed to create request for url: %s\n%w\n%s", url)
	}

	if strings.HasPrefix(authToken, "Basic ") {
		req.Header.Add("Authorization", authToken)
	} else if authToken != "" {
		req.Header.Add("Authorization", "Bearer "+authToken)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("DownloadFile: failed to download from url: %s\n%w\n%s", url, err, debug.Stack())
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("DownloadFile: bad http status: %s", resp.Status)
	}

	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return fmt.Errorf("DownloadFile: failed to write to file %s\n%w\n%s", filepath, err, debug.Stack())
	}

	fmt.Println("File downloaded successfully to " + filepath)
	return nil
}

func CreateTarGzArchive(files []string, buf io.Writer) error {
	gw := gzip.NewWriter(buf)
	defer gw.Close()
	tw := tar.NewWriter(gw)
	defer tw.Close()

	for _, file := range files {
		err := filepath.Walk(file, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return fmt.Errorf("CreateTarGzArchive: failed to open file %s\n%w\n%s", path, err, debug.Stack())
			}
			if !info.IsDir() {
				return addToArchive(tw, path)
			} else {
				header, err := tar.FileInfoHeader(info, path)
				if err != nil {
					return fmt.Errorf("CreateTarGzArchive: failed to get file info for %s\n%w\n%s", path, err, debug.Stack())
				}
				header.Name = path + "/"
				if err := tw.WriteHeader(header); err != nil {
					return fmt.Errorf("CreateTarGzArchive: failed to write file header for %s\n%w\n%s", path, err, debug.Stack())
				}
				return nil
			}
		})
		if err != nil {
			return fmt.Errorf("CreateTarGzArchive: failed to unpack files: %w\n%s", err, debug.Stack())
		}
	}

	return nil
}

func ExtractTarGzArchive(filename string, xpath string) error {
	tarFile, err := os.Open(filename)
	if err != nil {
		return fmt.Errorf("ExtractTarGzArchive: failed to open file %s, %w\n%s", filename, err, debug.Stack())
	}
	defer tarFile.Close()

	gz, err := gzip.NewReader(tarFile)
	if err != nil {
		return fmt.Errorf("ExtractTarGzArchive: failed to create gzip reader %w\n%s", err, debug.Stack())
	}
	defer gz.Close()

	absPath, err := filepath.Abs(xpath)
	if err != nil {
		return fmt.Errorf("ExtractTarGzArchive: failed to open extraction path %s\n%w\n%s", xpath, err, debug.Stack())
	}

	_, err = os.Stat(absPath)
	if errors.Is(err, os.ErrNotExist) {
		err = os.Mkdir(absPath, ReadWriteMode)
		if err != nil {
			return fmt.Errorf("ExtractTarGzArchive: failed to make directory %s\n%w\n%s", absPath, err, debug.Stack())
		}
	}

	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("ExtractTarGzArchive: %w\n%s", err, debug.Stack())
		}
		finfo := hdr.FileInfo()
		fileName := hdr.Name
		absFileName := filepath.Join(absPath, fileName)

		if finfo.Mode().IsDir() {
			if err := os.MkdirAll(absFileName, ReadWriteMode); err != nil {
				return fmt.Errorf("ExtractTarGzArchive: failed to create directory %s\n%w\n%s", absFileName, err, debug.Stack())
			}
			continue
		} else {
			parent := filepath.Dir(absFileName)
			_, err = os.Stat(parent)
			if errors.Is(err, os.ErrNotExist) {
				if err := os.MkdirAll(parent, ReadWriteMode); err != nil {
					return fmt.Errorf("ExtractTarGzArchive: failed to create directory %s\n%w\n%s", parent, err, debug.Stack())
				}
			}
		}
		file, err := os.OpenFile(
			absFileName,
			OpenFlagsForWriting,
			finfo.Mode().Perm(),
		)
		if err != nil {
			return fmt.Errorf("ExtractTarGzArchive: failed to open file %s\n%w\n%s", absFileName, err, debug.Stack())
		}
		fmt.Printf("x %s\n", absFileName)
		n, err := io.Copy(file, tr)
		if closeErr := file.Close(); closeErr != nil {
			return fmt.Errorf("ExtractTarGzArchive: failed to close file %s\n%w\n%s", absFileName, closeErr, debug.Stack())
		}
		if err != nil {
			return fmt.Errorf("ExtractTarGzArchive: failed to write to file %s\n%w\n%s", absFileName, err, debug.Stack())
		}
		if n != finfo.Size() {
			return fmt.Errorf("ExtractTarGzArchive: wrote %d, want %d", n, finfo.Size())
		}
	}
	return nil
}

func addToArchive(tw *tar.Writer, filename string) error {
	file, err := os.Open(filename)
	if err != nil {
		return fmt.Errorf("addToArchive: failed to open file %s\n%w\n%s", filename, err, debug.Stack())
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil {
		return fmt.Errorf("addToArchive: failed to stat file %s\n%w\n%s", filename, err, debug.Stack())
	}

	header, err := tar.FileInfoHeader(info, info.Name())
	if err != nil {
		return fmt.Errorf("addToArchive: failed to read file info header for file %s\n%w\n%s", filename, err, debug.Stack())
	}

	header.Name = filename

	err = tw.WriteHeader(header)
	if err != nil {
		return fmt.Errorf("addToArchive: failed to create header to archive %s\n%w\n%s", filename, err, debug.Stack())
	}

	_, err = io.Copy(tw, file)
	if err != nil {
		return fmt.Errorf("addToArchive: failed to write to archive %s\n%w\n%s", filename, err, debug.Stack())
	}

	return nil
}

func ZipSource(sources []string, target string) error {
	f, err := os.Create(target)
	if err != nil {
		return fmt.Errorf("ZipSource: failed to open file %s\n%w\n%s", target, err, debug.Stack())
	}
	defer f.Close()

	writer := zip.NewWriter(f)
	defer writer.Close()

	for _, source := range sources {
		err = filepath.Walk(source, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return fmt.Errorf("ZipSource: %w\n%s", err, debug.Stack())
			}

			header, err := zip.FileInfoHeader(info)
			if err != nil {
				return fmt.Errorf("ZipSource: failed to get file header for %s\n%w\n%s", path, err, debug.Stack())
			}

			header.Method = zip.Deflate

			header.Name = path
			header.Name = strings.ReplaceAll(header.Name, "\\", "/")
			if info.IsDir() {
				header.Name = strings.ReplaceAll(header.Name, "\\", "/")
				header.Name += "/"
			}

			headerWriter, err := writer.CreateHeader(header)
			if err != nil {
				return fmt.Errorf("ZipSource: failed to write header for path %s\n%w\n%s", path, err, debug.Stack())
			}

			if info.IsDir() {
				return nil
			}

			f, err := os.Open(path)
			if err != nil {
				return fmt.Errorf("ZipSource: failed to open file %s\n%w\n%s", path, err, debug.Stack())
			}
			defer f.Close()

			_, err = io.Copy(headerWriter, f)
			if err != nil {
				return fmt.Errorf("ZipSource: failed to write header for file %s\n%w\n%s", path, err, debug.Stack())
			}
			return nil
		})
		if err != nil {
			return fmt.Errorf("ZipSource: %w\n%s", err, debug.Stack())
		}
	}
	return nil
}

func UnzipSource(source, destination string) error {
	reader, err := zip.OpenReader(source)
	if err != nil {
		return fmt.Errorf("UnzipSource: failed to open file %s for reading\n%w\n%s", source, err, debug.Stack())
	}
	defer reader.Close()

	destinationAbsPath, err := filepath.Abs(destination)
	if err != nil {
		return fmt.Errorf("UnzipSource: failed to determine absolute path for %s\n%w\n%s", destination, err, debug.Stack())
	}
	destination = destinationAbsPath

	for _, f := range reader.File {
		err := unzipFile(f, destination)
		if err != nil {
			return fmt.Errorf("UnzipSource: failed to unzip file %s\n%w\n%s", source, err, debug.Stack())
		}
	}

	return nil
}

func unzipFile(f *zip.File, destination string) error {
	filePath := filepath.Join(destination, f.Name)
	if !strings.HasPrefix(filePath, filepath.Clean(destination)+string(os.PathSeparator)) {
		return fmt.Errorf("unzipFile: invalid file path: %s", filePath)
	}

	if f.FileInfo().IsDir() {
		if err := os.MkdirAll(filePath, os.ModePerm); err != nil {
			return fmt.Errorf("unzipFile: failed to create directory %s\n%w\n%s", filePath, err, debug.Stack())
		}
		return nil
	}

	if err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
		return fmt.Errorf("unzipFile: failed to create directory %s\n%w\n%s", filePath, err, debug.Stack())
	}

	destinationFile, err := os.OpenFile(filePath, OpenFlagsForWriting, f.Mode())
	if err != nil {
		return fmt.Errorf("unzipFile: failed to open file %s\n%w\n%s", filePath, err, debug.Stack())
	}
	defer destinationFile.Close()

	zippedFile, err := f.Open()
	if err != nil {
		return fmt.Errorf("unzipFile: failed to open file %s\n%w\n%s", filePath, err, debug.Stack())
	}
	defer zippedFile.Close()

	if _, err := io.Copy(destinationFile, zippedFile); err != nil {
		return fmt.Errorf("unzipFile: failed to write to file %s\n%w\n%s", destination, err, debug.Stack())
	}
	return nil
}
