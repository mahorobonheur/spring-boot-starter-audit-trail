package io.github.mahorobonheur.audittrail.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Static holder for the Spring {@link ApplicationContext}.
 *
 * <p>JPA {@code EntityListener} instances are created by Hibernate, not by Spring,
 * so {@code @Autowired} fields inside them are never populated. This class provides
 * a bridge: it is registered as a regular Spring bean and stores the context in a
 * static field, making it available to non-Spring-managed objects like entity listeners.
 *
 * @author Bonheur Mahoro
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * Returns the bean of the requested type from the application context.
     *
     * @param type the bean class
     * @param <T>  the type parameter
     * @return the matching bean
     * @throws IllegalStateException if the context has not been set yet
     */
    public static <T> T getBean(Class<T> type) {
        if (context == null) {
            throw new IllegalStateException(
                    "SpringContextHolder has not been initialised yet — " +
                    "ensure the Spring context is fully started before the first JPA operation.");
        }
        return context.getBean(type);
    }
}
