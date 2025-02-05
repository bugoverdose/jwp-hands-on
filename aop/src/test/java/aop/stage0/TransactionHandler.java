package aop.stage0;

import aop.DataAccessException;
import aop.Transactional;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * InvocationHandler is the interface implemented by the invocation handler of a proxy instance. Each proxy instance has
 * an associated invocation handler. When a method is invoked on a proxy instance, the method invocation is encoded and
 * dispatched to the invoke method of its invocation handler.
 */
public class TransactionHandler implements InvocationHandler {

    private final Object target;
    private final DataSource dataSource;

    public TransactionHandler(Object target, DataSource dataSource) {
        this.target = target;
        this.dataSource = dataSource;
    }

    /**
     * @Transactional 어노테이션이 존재하는 메서드만 트랜잭션 기능을 적용하도록 만들어보자.
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        Method instanceMethod = target.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
        if (!instanceMethod.isAnnotationPresent(Transactional.class)
            && !target.getClass().isAnnotationPresent(Transactional.class)) {
            return instanceMethod.invoke(target, args);
        }
        return executeTransaction(args, instanceMethod);
    }

    private Object executeTransaction(Object[] args, Method instanceMethod) {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        final var transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            Object result = instanceMethod.invoke(target, args);
            transactionManager.commit(transactionStatus);
            return result;
        } catch (Exception e) {
            transactionManager.rollback(transactionStatus);
            throw new DataAccessException(e);
        }
    }
}
