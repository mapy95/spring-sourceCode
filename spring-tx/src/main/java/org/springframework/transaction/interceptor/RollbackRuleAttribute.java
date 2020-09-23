/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.interceptor;

import java.io.Serializable;

import org.springframework.util.Assert;

/**
 * Rule determining whether or not a given exception (and any subclasses)
 * should cause a rollback.
 *
 * <p>Multiple such rules can be applied to determine whether a transaction
 * should commit or rollback after an exception has been thrown.
 *
 * @author Rod Johnson
 * @since 09.04.2003
 * @see NoRollbackRuleAttribute
 */
@SuppressWarnings("serial")
public class RollbackRuleAttribute implements Serializable{

	/**
	 * The {@link RollbackRuleAttribute rollback rule} for
	 * {@link RuntimeException RuntimeExceptions}.
	 */
	public static final RollbackRuleAttribute ROLLBACK_ON_RUNTIME_EXCEPTIONS =
			new RollbackRuleAttribute(RuntimeException.class);


	/**
	 * Could hold exception, resolving class name but would always require FQN.
	 * This way does multiple string comparisons, but how often do we decide
	 * whether to roll back a transaction following an exception?
	 */
	private final String exceptionName;


	/**
	 * Create a new instance of the {@code RollbackRuleAttribute} class.
	 * <p>This is the preferred way to construct a rollback rule that matches
	 * the supplied {@link Exception} class (and subclasses).
	 * @param clazz throwable class; must be {@link Throwable} or a subclass
	 * of {@code Throwable}
	 * @throws IllegalArgumentException if the supplied {@code clazz} is
	 * not a {@code Throwable} type or is {@code null}
	 */
	public RollbackRuleAttribute(Class<?> clazz) {
		Assert.notNull(clazz, "'clazz' cannot be null");
		if (!Throwable.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException(
					"Cannot construct rollback rule from [" + clazz.getName() + "]: it's not a Throwable");
		}
		this.exceptionName = clazz.getName();
	}

	/**
	 * Create a new instance of the {@code RollbackRuleAttribute} class
	 * for the given {@code exceptionName}.
	 * <p>This can be a substring, with no wildcard support at present. A value
	 * of "ServletException" would match
	 * {@code javax.servlet.ServletException} and subclasses, for example.
	 * <p><b>NB:</b> Consider carefully how specific the pattern is, and
	 * whether to include package information (which is not mandatory). For
	 * example, "Exception" will match nearly anything, and will probably hide
	 * other rules. "java.lang.Exception" would be correct if "Exception" was
	 * meant to define a rule for all checked exceptions. With more unusual
	 * exception names such as "BaseBusinessException" there's no need to use a
	 * fully package-qualified name.
	 * @param exceptionName the exception name pattern; can also be a fully
	 * package-qualified class name
	 * @throws IllegalArgumentException if the supplied
	 * {@code exceptionName} is {@code null} or empty
	 */
	public RollbackRuleAttribute(String exceptionName) {
		Assert.hasText(exceptionName, "'exceptionName' cannot be null or empty");
		this.exceptionName = exceptionName;
	}


	/**
	 * Return the pattern for the exception name.
	 */
	public String getExceptionName() {
		return exceptionName;
	}

	/**
	 * Return the depth of the superclass matching.
	 * <p>{@code 0} means {@code ex} matches exactly. Returns
	 * {@code -1} if there is no match. Otherwise, returns depth with the
	 * lowest depth winning.
	 */
	public int getDepth(Throwable ex) {
		return getDepth(ex.getClass(), 0);
	}


	/**
	 * 这里其实就是用业务代码咆抛出的异常和程序员在@Transactional注解中执行的异常信息进行对比
	 *
	 * 实际底层使用的是String.contains()方法
	 * exceptionName：就是程序员指定的异常对应的类名；比如：我执行的是rollbackFor = Exception.class，那这里的exceptionName就是：java.lang.Exception
	 *
	 * 1.如果当前抛出的异常和程序员指定的异常匹配不上，就依次递归调用抛出异常的父类和程序员指定的异常进行比较，
	 * 		1.1 直到匹配上，就返回当前的depth，depth每递归调用一次，就+1
	 * 		1.2	或者是到Throwable依旧没有比对上，这时，就表示我指定的异常和代码抛出的异常不匹配
	 *
	 * 	这两种场景也好验证：
	 * 		1.首先，我在业务代码中，加上这么一行代码：int i = 10/0;
	 * 		2.然后在@Transactional注解中加上rollbackException = Exception.class	/rollbackException = IoException.class
	 * 	    这两种异常，最后事务都会回滚，但是效果却是不一样的
	 * 	    如果我加的是rollbackException = Exception.class，这里会匹配上，返回的是depth是2
	 * 	    但是如果加的是rollbackException = IoException.class，这里返回的是-1
	 *
	 * 	    因为：如果是rollbackException = Exception.class；那这里在匹配的时候，会递归调用两次，
	 * 	    int i = 10/0;会抛出java.lang.ArithmeticException: / by zero
	 * 	    ArithmeticException的父类是RuntimeException；RuntimeException的父类是Exception；所以只有递归调用两次，才能匹配到我指定的Exception.class
	 *
	 * 	    但是，如果我指定的是IoException.class，那用于也匹配不上，因为IOException和ArithmeticException都继承了RuntimeException，是并行的关系，在最后
	 * 	    递归调用到父类Throwable的时候，就会返回-1
	 *
	 * @param exceptionClass：当前业务代码抛出的异常
	 * @param depth：相似度/或者说是深度
	 * @return
	 */
	private int getDepth(Class<?> exceptionClass, int depth) {
		if (exceptionClass.getName().contains(this.exceptionName)) {
			// Found it!
			return depth;
		}
		// If we've gone as far as we can go and haven't found it...
		if (exceptionClass == Throwable.class) {
			return -1;
		}
		return getDepth(exceptionClass.getSuperclass(), depth + 1);
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RollbackRuleAttribute)) {
			return false;
		}
		RollbackRuleAttribute rhs = (RollbackRuleAttribute) other;
		return this.exceptionName.equals(rhs.exceptionName);
	}

	@Override
	public int hashCode() {
		return this.exceptionName.hashCode();
	}

	@Override
	public String toString() {
		return "RollbackRuleAttribute with pattern [" + this.exceptionName + "]";
	}

}
