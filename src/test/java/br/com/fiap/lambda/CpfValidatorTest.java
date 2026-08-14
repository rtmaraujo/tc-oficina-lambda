package br.com.fiap.lambda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfValidatorTest {

    @Test
    @DisplayName("deve aceitar CPF valido")
    void deveAceitarCpfValido() {
        assertTrue(CpfValidator.isValidCpf("12345678909"));
        assertTrue(CpfValidator.isValidCpf("52998224725"));
    }

    @Test
    @DisplayName("deve aceitar CPF com mascara de pontuacao")
    void deveAceitarCpfComPontuacao() {
        assertTrue(CpfValidator.isValidCpf("123.456.789-09"));
        assertTrue(CpfValidator.isValidCpf("529.982.247-25"));
    }

    @Test
    @DisplayName("deve rejeitar CPF com digitos repetidos")
    void deveRejeitarDigitosRepetidos() {
        assertFalse(CpfValidator.isValidCpf("11111111111"));
        assertFalse(CpfValidator.isValidCpf("00000000000"));
        assertFalse(CpfValidator.isValidCpf("99999999999"));
    }

    @Test
    @DisplayName("deve rejeitar CPF com checagem invalida")
    void deveRejeitarCpfInvalido() {
        assertFalse(CpfValidator.isValidCpf("12345678900"));
        assertFalse(CpfValidator.isValidCpf("12345678910"));
    }

    @Test
    @DisplayName("deve rejeitar tamanhos e entradas invalidas")
    void deveRejeitarEntradasInvalidas() {
        assertFalse(CpfValidator.isValidCpf(null));
        assertFalse(CpfValidator.isValidCpf(""));
        assertFalse(CpfValidator.isValidCpf("123"));
        assertFalse(CpfValidator.isValidCpf("1234567890"));
        assertFalse(CpfValidator.isValidCpf("abcdefghijk"));
        assertFalse(CpfValidator.isValidCpf("12345678909123"));
    }
}
