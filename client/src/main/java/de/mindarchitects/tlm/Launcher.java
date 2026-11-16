package de.mindarchitects.tlm;

/**
 * Einstiegspunkt für das gepackte Programm. Eine Klasse, die nicht von Application erbt,
 * erlaubt den Start vom Classpath ohne "JavaFX runtime components are missing".
 */
public final class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
