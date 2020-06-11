// Copyright 2016 Qiang Xue. All rights reserved.
// Use of this source code is governed by a MIT-style
// license that can be found in the LICENSE file.

// Package is provides a list of commonly used string validation rules.
package is

import (
	validation "github.com/go-ozzo/ozzo-validation/v4"
	"regexp"
	"unicode"

	"github.com/asaskevich/govalidator"
)

var (
	// ErrEmail is the error that returns in case of an invalid email.
	ErrEmail = validation.NewError("validation_is_email", "must be a valid email address")
	// ErrURL is the error that returns in case of an invalid URL.
	ErrURL = validation.NewError("validation_is_url", "must be a valid URL")
	// ErrRequestURL is the error that returns in case of an invalid request URL.
	ErrRequestURL = validation.NewError("validation_is_request_url", "must be a valid request URL")
	// ErrRequestURI is the error that returns in case of an invalid request URI.
	ErrRequestURI = validation.NewError("validation_request_is_request_uri", "must be a valid request URI")
	// ErrAlpha is the error that returns in case of an invalid alpha value.
	ErrAlpha = validation.NewError("validation_is_alpha", "must contain English letters only")
	// ErrDigit is the error that returns in case of an invalid digit value.
	ErrDigit = validation.NewError("validation_is_digit", "must contain digits only")
	// ErrAlphanumeric is the error that returns in case of an invalid alphanumeric value.
	ErrAlphanumeric = validation.NewError("validation_is_alphanumeric", "must contain English letters and digits only")
	// ErrUTFLetter is the error that returns in case of an invalid utf letter value.
	ErrUTFLetter = validation.NewError("validation_is_utf_letter", "must contain unicode letter characters only")
	// ErrUTFDigit is the error that returns in case of an invalid utf digit value.
	ErrUTFDigit = validation.NewError("validation_is_utf_digit", "must contain unicode decimal digits only")
	// ErrUTFLetterNumeric is the error that returns in case of an invalid utf numeric or letter value.
	ErrUTFLetterNumeric = validation.NewError("validation_is utf_letter_numeric", "must contain unicode letters and numbers only")
	// ErrUTFNumeric is the error that returns in case of an invalid utf numeric value.
	ErrUTFNumeric = validation.NewError("validation_is_utf_numeric", "must contain unicode number characters only")
	// ErrLowerCase is the error that returns in case of an invalid lower case value.
	ErrLowerCase = validation.NewError("validation_is_lower_case", "must be in lower case")
	// ErrUpperCase is the error that returns in case of an invalid upper case value.
	ErrUpperCase = validation.NewError("validation_is_upper_case", "must be in upper case")
	// ErrHexadecimal is the error that returns in case of an invalid hexadecimal number.
	ErrHexadecimal = validation.NewError("validation_is_hexadecimal", "must be a valid hexadecimal number")
	// ErrHexColor is the error that returns in case of an invalid hexadecimal color code.
	ErrHexColor = validation.NewError("validation_is_hex_color", "must be a valid hexadecimal color code")
	// ErrRGBColor is the error that returns in case of an invalid RGB color code.
	ErrRGBColor = validation.NewError("validation_is_rgb_color", "must be a valid RGB color code")
	// ErrInt is the error that returns in case of an invalid integer value.
	ErrInt = validation.NewError("validation_is_int", "must be an integer number")
	// ErrFloat is the error that returns in case of an invalid float value.
	ErrFloat = validation.NewError("validation_is_float", "must be a floating point number")
	// ErrUUIDv3 is the error that returns in case of an invalid UUIDv3 value.
	ErrUUIDv3 = validation.NewError("validation_is_uuid_v3", "must be a valid UUID v3")
	// ErrUUIDv4 is the error that returns in case of an invalid UUIDv4 value.
	ErrUUIDv4 = validation.NewError("validation_is_uuid_v4", "must be a valid UUID v4")
	// ErrUUIDv5 is the error that returns in case of an invalid UUIDv5 value.
	ErrUUIDv5 = validation.NewError("validation_is_uuid_v5", "must be a valid UUID v5")
	// ErrUUID is the error that returns in case of an invalid UUID value.
	ErrUUID = validation.NewError("validation_is_uuid", "must be a valid UUID")
	// ErrCreditCard is the error that returns in case of an invalid credit card number.
	ErrCreditCard = validation.NewError("validation_is_credit_card", "must be a valid credit card number")
	// ErrISBN10 is the error that returns in case of an invalid ISBN-10 value.
	ErrISBN10 = validation.NewError("validation_is_isbn_10", "must be a valid ISBN-10")
	// ErrISBN13 is the error that returns in case of an invalid ISBN-13 value.
	ErrISBN13 = validation.NewError("validation_is_isbn_13", "must be a valid ISBN-13")
	// ErrISBN is the error that returns in case of an invalid ISBN value.
	ErrISBN = validation.NewError("validation_is_isbn", "must be a valid ISBN")
	// ErrJSON is the error that returns in case of an invalid JSON.
	ErrJSON = validation.NewError("validation_is_json", "must be in valid JSON format")
	// ErrASCII is the error that returns in case of an invalid ASCII.
	ErrASCII = validation.NewError("validation_is_ascii", "must contain ASCII characters only")
	// ErrPrintableASCII is the error that returns in case of an invalid printable ASCII value.
	ErrPrintableASCII = validation.NewError("validation_is_printable_ascii", "must contain printable ASCII characters only")
	// ErrMultibyte is the error that returns in case of an invalid multibyte value.
	ErrMultibyte = validation.NewError("validation_is_multibyte", "must contain multibyte characters")
	// ErrFullWidth is the error that returns in case of an invalid full-width value.
	ErrFullWidth = validation.NewError("validation_is_full_width", "must contain full-width characters")
	// ErrHalfWidth is the error that returns in case of an invalid half-width value.
	ErrHalfWidth = validation.NewError("validation_is_half_width", "must contain half-width characters")
	// ErrVariableWidth is the error that returns in case of an invalid variable width value.
	ErrVariableWidth = validation.NewError("validation_is_variable_width", "must contain both full-width and half-width characters")
	// ErrBase64 is the error that returns in case of an invalid base54 value.
	ErrBase64 = validation.NewError("validation_is_base64", "must be encoded in Base64")
	// ErrDataURI is the error that returns in case of an invalid data URI.
	ErrDataURI = validation.NewError("validation_is_data_uri", "must be a Base64-encoded data URI")
	// ErrE164 is the error that returns in case of an invalid e165.
	ErrE164 = validation.NewError("validation_is_e164_number", "must be a valid E164 number")
	// ErrCountryCode2 is the error that returns in case of an invalid two-letter country code.
	ErrCountryCode2 = validation.NewError("validation_is_country_code_2_letter", "must be a valid two-letter country code")
	// ErrCountryCode3 is the error that returns in case of an invalid three-letter country code.
	ErrCountryCode3 = validation.NewError("validation_is_country_code_3_letter", "must be a valid three-letter country code")
	// ErrCurrencyCode is the error that returns in case of an invalid currency code.
	ErrCurrencyCode = validation.NewError("validation_is_currency_code", "must be valid ISO 4217 currency code")
	// ErrDialString is the error that returns in case of an invalid string.
	ErrDialString = validation.NewError("validation_is_dial_string", "must be a valid dial string")
	// ErrMac is the error that returns in case of an invalid mac address.
	ErrMac = validation.NewError("validation_is_mac_address", "must be a valid MAC address")
	// ErrIP is the error that returns in case of an invalid IP.
	ErrIP = validation.NewError("validation_is_ip", "must be a valid IP address")
	// ErrIPv4 is the error that returns in case of an invalid IPv4.
	ErrIPv4 = validation.NewError("validation_is_ipv4", "must be a valid IPv4 address")
	// ErrIPv6 is the error that returns in case of an invalid IPv6.
	ErrIPv6 = validation.NewError("validation_is_ipv6", "must be a valid IPv6 address")
	// ErrSubdomain is the error that returns in case of an invalid subdomain.
	ErrSubdomain = validation.NewError("validation_is_sub_domain", "must be a valid subdomain")
	// ErrDomain is the error that returns in case of an invalid domain.
	ErrDomain = validation.NewError("validation_is_domain", "must be a valid domain")
	// ErrDNSName is the error that returns in case of an invalid DNS name.
	ErrDNSName = validation.NewError("validation_is_dns_name", "must be a valid DNS name")
	// ErrHost is the error that returns in case of an invalid host.
	ErrHost = validation.NewError("validation_is_host", "must be a valid IP address or DNS name")
	// ErrPort is the error that returns in case of an invalid port.
	ErrPort = validation.NewError("validation_is_port", "must be a valid port number")
	// ErrMongoID is the error that returns in case of an invalid MongoID.
	ErrMongoID = validation.NewError("validation_is_mongo_id", "must be a valid hex-encoded MongoDB ObjectId")
	// ErrLatitude is the error that returns in case of an invalid latitude.
	ErrLatitude = validation.NewError("validation_is_latitude", "must be a valid latitude")
	// ErrLongitude is the error that returns in case of an invalid longitude.
	ErrLongitude = validation.NewError("validation_is_longitude", "must be a valid longitude")
	// ErrSSN is the error that returns in case of an invalid SSN.
	ErrSSN = validation.NewError("validation_is_ssn", "must be a valid social security number")
	// ErrSemver is the error that returns in case of an invalid semver.
	ErrSemver = validation.NewError("validation_is_semver", "must be a valid semantic version")
)

var (
	// Email validates if a string is an email or not. It also checks if the MX record exists for the email domain.
	Email = validation.NewStringRuleWithError(govalidator.IsExistingEmail, ErrEmail)
	// EmailFormat validates if a string is an email or not. Note that it does NOT check if the MX record exists or not.
	EmailFormat = validation.NewStringRuleWithError(govalidator.IsEmail, ErrEmail)
	// URL validates if a string is a valid URL
	URL = validation.NewStringRuleWithError(govalidator.IsURL, ErrURL)
	// RequestURL validates if a string is a valid request URL
	RequestURL = validation.NewStringRuleWithError(govalidator.IsRequestURL, ErrRequestURL)
	// RequestURI validates if a string is a valid request URI
	RequestURI = validation.NewStringRuleWithError(govalidator.IsRequestURI, ErrRequestURI)
	// Alpha validates if a string contains English letters only (a-zA-Z)
	Alpha = validation.NewStringRuleWithError(govalidator.IsAlpha, ErrAlpha)
	// Digit validates if a string contains digits only (0-9)
	Digit = validation.NewStringRuleWithError(isDigit, ErrDigit)
	// Alphanumeric validates if a string contains English letters and digits only (a-zA-Z0-9)
	Alphanumeric = validation.NewStringRuleWithError(govalidator.IsAlphanumeric, ErrAlphanumeric)
	// UTFLetter validates if a string contains unicode letters only
	UTFLetter = validation.NewStringRuleWithError(govalidator.IsUTFLetter, ErrUTFLetter)
	// UTFDigit validates if a string contains unicode decimal digits only
	UTFDigit = validation.NewStringRuleWithError(govalidator.IsUTFDigit, ErrUTFDigit)
	// UTFLetterNumeric validates if a string contains unicode letters and numbers only
	UTFLetterNumeric = validation.NewStringRuleWithError(govalidator.IsUTFLetterNumeric, ErrUTFLetterNumeric)
	// UTFNumeric validates if a string contains unicode number characters (category N) only
	UTFNumeric = validation.NewStringRuleWithError(isUTFNumeric, ErrUTFNumeric)
	// LowerCase validates if a string contains lower case unicode letters only
	LowerCase = validation.NewStringRuleWithError(govalidator.IsLowerCase, ErrLowerCase)
	// UpperCase validates if a string contains upper case unicode letters only
	UpperCase = validation.NewStringRuleWithError(govalidator.IsUpperCase, ErrUpperCase)
	// Hexadecimal validates if a string is a valid hexadecimal number
	Hexadecimal = validation.NewStringRuleWithError(govalidator.IsHexadecimal, ErrHexadecimal)
	// HexColor validates if a string is a valid hexadecimal color code
	HexColor = validation.NewStringRuleWithError(govalidator.IsHexcolor, ErrHexColor)
	// RGBColor validates if a string is a valid RGB color in the form of rgb(R, G, B)
	RGBColor = validation.NewStringRuleWithError(govalidator.IsRGBcolor, ErrRGBColor)
	// Int validates if a string is a valid integer number
	Int = validation.NewStringRuleWithError(govalidator.IsInt, ErrInt)
	// Float validates if a string is a floating point number
	Float = validation.NewStringRuleWithError(govalidator.IsFloat, ErrFloat)
	// UUIDv3 validates if a string is a valid version 3 UUID
	UUIDv3 = validation.NewStringRuleWithError(govalidator.IsUUIDv3, ErrUUIDv3)
	// UUIDv4 validates if a string is a valid version 4 UUID
	UUIDv4 = validation.NewStringRuleWithError(govalidator.IsUUIDv4, ErrUUIDv4)
	// UUIDv5 validates if a string is a valid version 5 UUID
	UUIDv5 = validation.NewStringRuleWithError(govalidator.IsUUIDv5, ErrUUIDv5)
	// UUID validates if a string is a valid UUID
	UUID = validation.NewStringRuleWithError(govalidator.IsUUID, ErrUUID)
	// CreditCard validates if a string is a valid credit card number
	CreditCard = validation.NewStringRuleWithError(govalidator.IsCreditCard, ErrCreditCard)
	// ISBN10 validates if a string is an ISBN version 10
	ISBN10 = validation.NewStringRuleWithError(govalidator.IsISBN10, ErrISBN10)
	// ISBN13 validates if a string is an ISBN version 13
	ISBN13 = validation.NewStringRuleWithError(govalidator.IsISBN13, ErrISBN13)
	// ISBN validates if a string is an ISBN (either version 10 or 13)
	ISBN = validation.NewStringRuleWithError(isISBN, ErrISBN)
	// JSON validates if a string is in valid JSON format
	JSON = validation.NewStringRuleWithError(govalidator.IsJSON, ErrJSON)
	// ASCII validates if a string contains ASCII characters only
	ASCII = validation.NewStringRuleWithError(govalidator.IsASCII, ErrASCII)
	// PrintableASCII validates if a string contains printable ASCII characters only
	PrintableASCII = validation.NewStringRuleWithError(govalidator.IsPrintableASCII, ErrPrintableASCII)
	// Multibyte validates if a string contains multibyte characters
	Multibyte = validation.NewStringRuleWithError(govalidator.IsMultibyte, ErrMultibyte)
	// FullWidth validates if a string contains full-width characters
	FullWidth = validation.NewStringRuleWithError(govalidator.IsFullWidth, ErrFullWidth)
	// HalfWidth validates if a string contains half-width characters
	HalfWidth = validation.NewStringRuleWithError(govalidator.IsHalfWidth, ErrHalfWidth)
	// VariableWidth validates if a string contains both full-width and half-width characters
	VariableWidth = validation.NewStringRuleWithError(govalidator.IsVariableWidth, ErrVariableWidth)
	// Base64 validates if a string is encoded in Base64
	Base64 = validation.NewStringRuleWithError(govalidator.IsBase64, ErrBase64)
	// DataURI validates if a string is a valid base64-encoded data URI
	DataURI = validation.NewStringRuleWithError(govalidator.IsDataURI, ErrDataURI)
	// E164 validates if a string is a valid ISO3166 Alpha 2 country code
	E164 = validation.NewStringRuleWithError(isE164Number, ErrE164)
	// CountryCode2 validates if a string is a valid ISO3166 Alpha 2 country code
	CountryCode2 = validation.NewStringRuleWithError(govalidator.IsISO3166Alpha2, ErrCountryCode2)
	// CountryCode3 validates if a string is a valid ISO3166 Alpha 3 country code
	CountryCode3 = validation.NewStringRuleWithError(govalidator.IsISO3166Alpha3, ErrCountryCode3)
	// CurrencyCode validates if a string is a valid IsISO4217 currency code.
	CurrencyCode = validation.NewStringRuleWithError(govalidator.IsISO4217, ErrCurrencyCode)
	// DialString validates if a string is a valid dial string that can be passed to Dial()
	DialString = validation.NewStringRuleWithError(govalidator.IsDialString, ErrDialString)
	// MAC validates if a string is a MAC address
	MAC = validation.NewStringRuleWithError(govalidator.IsMAC, ErrMac)
	// IP validates if a string is a valid IP address (either version 4 or 6)
	IP = validation.NewStringRuleWithError(govalidator.IsIP, ErrIP)
	// IPv4 validates if a string is a valid version 4 IP address
	IPv4 = validation.NewStringRuleWithError(govalidator.IsIPv4, ErrIPv4)
	// IPv6 validates if a string is a valid version 6 IP address
	IPv6 = validation.NewStringRuleWithError(govalidator.IsIPv6, ErrIPv6)
	// Subdomain validates if a string is valid subdomain
	Subdomain = validation.NewStringRuleWithError(isSubdomain, ErrSubdomain)
	// Domain validates if a string is valid domain
	Domain = validation.NewStringRuleWithError(isDomain, ErrDomain)
	// DNSName validates if a string is valid DNS name
	DNSName = validation.NewStringRuleWithError(govalidator.IsDNSName, ErrDNSName)
	// Host validates if a string is a valid IP (both v4 and v6) or a valid DNS name
	Host = validation.NewStringRuleWithError(govalidator.IsHost, ErrHost)
	// Port validates if a string is a valid port number
	Port = validation.NewStringRuleWithError(govalidator.IsPort, ErrPort)
	// MongoID validates if a string is a valid Mongo ID
	MongoID = validation.NewStringRuleWithError(govalidator.IsMongoID, ErrMongoID)
	// Latitude validates if a string is a valid latitude
	Latitude = validation.NewStringRuleWithError(govalidator.IsLatitude, ErrLatitude)
	// Longitude validates if a string is a valid longitude
	Longitude = validation.NewStringRuleWithError(govalidator.IsLongitude, ErrLongitude)
	// SSN validates if a string is a social security number (SSN)
	SSN = validation.NewStringRuleWithError(govalidator.IsSSN, ErrSSN)
	// Semver validates if a string is a valid semantic version
	Semver = validation.NewStringRuleWithError(govalidator.IsSemver, ErrSemver)
)

var (
	reDigit = regexp.MustCompile("^[0-9]+$")
	// Subdomain regex source: https://stackoverflow.com/a/7933253
	reSubdomain = regexp.MustCompile(`^[A-Za-z0-9](?:[A-Za-z0-9\-]{0,61}[A-Za-z0-9])?$`)
	// E164 regex source: https://stackoverflow.com/a/23299989
	reE164 = regexp.MustCompile(`^\+?[1-9]\d{1,14}$`)
	// Domain regex source: https://stackoverflow.com/a/7933253
	// Slightly modified: Removed 255 max length validation since Go regex does not
	// support lookarounds. More info: https://stackoverflow.com/a/38935027
	reDomain = regexp.MustCompile(`^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-z0-9])?\.)+(?:[a-zA-Z]{1,63}| xn--[a-z0-9]{1,59})$`)
)

func isISBN(value string) bool {
	return govalidator.IsISBN(value, 10) || govalidator.IsISBN(value, 13)
}

func isDigit(value string) bool {
	return reDigit.MatchString(value)
}

func isE164Number(value string) bool {
	return reE164.MatchString(value)
}

func isSubdomain(value string) bool {
	return reSubdomain.MatchString(value)
}

func isDomain(value string) bool {
	if len(value) > 255 {
		return false
	}

	return reDomain.MatchString(value)
}

func isUTFNumeric(value string) bool {
	for _, c := range value {
		if unicode.IsNumber(c) == false {
			return false
		}
	}
	return true
}
