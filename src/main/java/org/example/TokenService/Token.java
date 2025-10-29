package org.example.TokenService;

public final class Token {

    private String valor;

    public Token(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("O valor do token nao pode ser nulo ou vazio.");
        }
        this.valor = valor;
    }

    public Token() {

    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return this.valor;
    }

    @Override
    public String toString() {
        return "Token[valor=" + valor + "]";
    }
}