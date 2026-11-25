package com.newgen.los.model;

public record Branch(String code, String name) {

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
