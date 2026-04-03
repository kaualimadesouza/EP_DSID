package br.usp.agv.model;

public record Order(String orderId, Position pickup, Position delivery) {}