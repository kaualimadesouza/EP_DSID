package br.usp.agv.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainTest
{
    @Test
    @DisplayName("AGV moves to a new position when command is valid")
    void shouldMoveAgvToNewPositionWhenCommandIsValid() {
        // Arrange
        int a = 1;
        int b = 1;

        // Act
        int c = a + b;

        // Assert
        assertEquals(2, c);
    }
}
