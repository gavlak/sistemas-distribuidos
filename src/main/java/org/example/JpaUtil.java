package org.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JpaUtil {

    private static final EntityManagerFactory factory;

    static {
        try {
            // Procura pelo arquivo META-INF/persistence.xml
            factory = Persistence.createEntityManagerFactory("meu-projeto-pu");
        } catch (Throwable ex) {
            System.err.println("A criação do EntityManagerFactory falhou." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static EntityManager getEntityManager() {
        return factory.createEntityManager();
    }

    public static void shutdown() {
        if (factory != null) {
            factory.close();
        }
    }
}