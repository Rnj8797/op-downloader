package com.opdownloader.app.domain

import com.opdownloader.app.domain.usecase.ValidateUrlUseCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ValidateUrlUseCaseTest {

    private lateinit var validateUrlUseCase: ValidateUrlUseCase

    @Before
    fun setUp() {
        validateUrlUseCase = ValidateUrlUseCase()
    }

    @Test
    fun `valid public https url passes validation`() {
        val result = validateUrlUseCase("https://commondatastorage.googleapis.com/sample/video.mp4")
        assertTrue(result is ValidateUrlUseCase.ValidationResult.Valid)
        assertEquals(
            "https://commondatastorage.googleapis.com/sample/video.mp4",
            (result as ValidateUrlUseCase.ValidationResult.Valid).normalizedUrl
        )
    }

    @Test
    fun `insecure http scheme is rejected`() {
        val result = validateUrlUseCase("http://example.com/media.mp4")
        assertTrue(result is ValidateUrlUseCase.ValidationResult.Invalid)
        assertEquals("Only secure HTTPS links are supported.", (result as ValidateUrlUseCase.ValidationResult.Invalid).reason)
    }

    @Test
    fun `dangerous file and ftp schemes are rejected`() {
        val fileResult = validateUrlUseCase("file:///etc/passwd")
        assertTrue(fileResult is ValidateUrlUseCase.ValidationResult.Invalid)

        val ftpResult = validateUrlUseCase("ftp://example.com/media.mp4")
        assertTrue(ftpResult is ValidateUrlUseCase.ValidationResult.Invalid)
    }

    @Test
    fun `localhost and loopback addresses are rejected for SSRF protection`() {
        val localhostResult = validateUrlUseCase("https://localhost/admin")
        assertTrue(localhostResult is ValidateUrlUseCase.ValidationResult.Invalid)

        val loopbackResult = validateUrlUseCase("https://127.0.0.1:8080/data")
        assertTrue(loopbackResult is ValidateUrlUseCase.ValidationResult.Invalid)
    }

    @Test
    fun `cloud metadata ip is strictly rejected`() {
        val metadataResult = validateUrlUseCase("https://169.254.169.254/latest/meta-data")
        assertTrue(metadataResult is ValidateUrlUseCase.ValidationResult.Invalid)
    }

    @Test
    fun `private rfc1918 subnets are rejected`() {
        val sub10 = validateUrlUseCase("https://10.0.0.5/video.mp4")
        assertTrue(sub10 is ValidateUrlUseCase.ValidationResult.Invalid)

        val sub192 = validateUrlUseCase("https://192.168.1.100/video.mp4")
        assertTrue(sub192 is ValidateUrlUseCase.ValidationResult.Invalid)
    }

    @Test
    fun `empty or blank urls are rejected`() {
        val emptyResult = validateUrlUseCase("   ")
        assertTrue(emptyResult is ValidateUrlUseCase.ValidationResult.Invalid)
    }
}
