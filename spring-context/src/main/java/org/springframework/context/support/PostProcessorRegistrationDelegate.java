/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	/**
	 * @param beanFactory
	 * @param beanFactoryPostProcessors
	 *
	 * 这里是对beanDefinitionRegistryPostProcessor和beanFactoryPostProcessor实现类的出来
	 * 1.先执行程序员通过API提供的beanDefinitionRegistryPostProcessor的实现类
	 * 2.执行spring中，实现了PriorityOrdered接口且实现了beanDefinitionRegistryPostProcessor接口的实现类
	 * 3.执行spring中，实现了Ordered接口且实现了beanDefinitionRegistryPostProcessor接口的实现类
	 * 4.执行spring中，只实现了beanDefinitionRegistryPostProcessor接口的实现类
	 * 5.执行实现了beanFactoryPostProcessor接口的实现类
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		//这个set集合可以理解为存储的是已经执行过的beanDefinitionRegistryPostProcessor
		Set<String> processedBeans = new HashSet<>();

		/**
		 * 这里之所以要判断beanFactory是哪种类型的，应该和org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
		 * 的入参有关系
		 */
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			//regularPostProcessors这个list存储的是beanFactoryPostProcessor接口的实现类
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			//registryProcessors 这个list存储的是beanDefinitionRegistryPostProcessor接口的实现类
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			/**
			 * 序号1
			 * 这里的beanFactoryPostProcessors是程序员自己提供的BeanFactoryPostProcessor的实现类
			 */
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				/**
				 * 区分当前bean是BeanDefinitionRegistryPostProcessor还是 beanFactoryPostProcessor
				 * 因为前者是后者的子类，所以在获取beanFactoryPostprocessor的时候 也可以获取到
				 *
				 * 在本方法中  是先执行实现了BeanDefinitionRegistryPostProcessor的类
				 * 再执行beanFactoryPostProcessor的类
				 * 序号1.1
				 */
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					// 这里会进行一个强转，因为有可能这里的postProcessor是BeanDefinitionRegistryPostProcessor的子接口的实现类，所以向上转型
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					//这里直接执行程序员通过API注入的beanDefinitionRegistryPostProcessor的实现类
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					// 序号1.2 将beanFactoryPostProcessor添加到regularPostProcessors;执行到这里，说明postProcessor是BeanFactoryPostProcessor的实现类
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			//这里的这个list是用来存放spring中实现了beanFactoryRegistryPostProcessor接口的实现类
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			/**
			 * 序号2
			 *获取到了一个BeanDefinitionRegistryPostProcessor的实现类 --  ConfigurationClassPostprocessor
			 * 并且这里也只会获取到一个，因为截止到目前，spring容器中还没有我们自己写的业务类，只有spring自己注入的集合bean；
			 * 而这几个bean中，只有ConfigurationClassPostProcessor是BeanDefinitionRegistryPostProcessor的实现类
			 *
			 * 需要注意的是：
			 * 	这个类在spring解析扫描初始化的时候用到了，ConfigurationClassPostProcessor是最重要的一个
			 *
			 * 所谓的合并bean，就是判断当前bean是否有父beanDefinition，如果有父beanDefinition,就把父beanDefinition和子beanDefinition合并到一起
			 * 在这里获取beanDefinitionRegistryPostProcessor类型的bean的时候，会对bean一个合并，也就是所谓的mergeBean
			 * 在后面finishBeanFactoryInitialization方法中，对bean进行实例化的时候，会再判断一次
			 */
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			/**
			 * 序号3
			 * spring内部在执行BeanDefinitionRegistryPostProcessor的实现类的时候，是由顺序的
			 * 	实现了PriorityOrdered接口的类 -->  实现了Ordered接口的类  --> other
			 *
			 */
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			//排序，合并spring自己的和程序员自定义的beanFactoryRegistryPostProcessor
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			/**
			 * 序号3.1
			 * invokeBeanDefinitionRegistryPostProcessors这行代码，在本方法中调用了三次
			 * 这里是第一次调用，这是，理论上只有一个，就是ConfigurationClassPostProcessor
			 *
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				/**
				 * 可以看到，在第二次和第三次从容器中获取实现类的时候，会先从已经执行过的集合中进行过滤(processedBeans)过滤
				 */
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			/**
			 * 序号3.2
			 * 这里是第二次调用，执行实现了Ordered接口的类
			 */
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			/**
			 * 序号3.3
			 * 这里执行的是所有实现了beanDefinitionRegistryPostProcessor且无实现其他接口的类
			 *
			 * 这里为什么要用while(true)？
			 *  因为有可能beanDefinitionRegistryPostProcessor的实现类中有可能会又注入了一个beanDefinitionRegistryPostProcessor的实现类，所以这里要循环查找并执行;
			 *  如果第二次从beanFactory中没有找到beanDefinitionRegistryPostProcessor的实现类，那么，这里reiterate就是false，就不会再执行了
			 *  如果第二次从beanFactory中找到了未执行的实现类，那么就会继续执行，同时，reiterate变为true；然后进行下一轮的循环
			 */
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			/**
			 * 序号4
			 * 前面说了，registryProcessors是保存beanDefinitionRegistryPostProcessor接口的实现类
			 * regularPostProcessors保存的是程序员提供的beanFactoryPostProcessor接口的实现类，
			 * 那为什么这里还会有 invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);这行代码？
			 *  原因很简单，一个接口在实现beanDefinitionRegistryPostProcessor接口的同时，必然会实现beanFactoryPostProcessor接口
			 *  所以，这里要执行
			 *  registryProcessors中是spring内置的beanFactoryPostProcessor
			 *  regularPostProcessors是程序员提供的beanFactoryPostProcessor
			 *
			 *  所以，spring源码中，
			 *  	1.会先执行程序员提供的BeanDefinitionRegistryPostProcessor接口的实现类，
			 *  	2.会执行spring自带的	BeanDefinitionRegistryPostProcessor接口的实现类
			 *  	3.然后执行spring自带的	BeanDefinitionRegistryPostProcessor接口的实现类中的postProcessBeanFactory方法
			 *  	4.最后执行程序员提供的	BeanDefinitionRegistryPostProcessor接口的实现类中的postProcessBeanFactory方法
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			/**
			 *  如果beanFactory不是BeanDefinitionRegistry类型，就执行BeanFactoryPostProcessor的实现类
			 */
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		/**
		 * 这里的是程序员提供的beanFactoryPostProcessor的实现类，是通过@Component注解提供的，而不是ac.addBeanFactoryPostProcessor提供的
		 *
		 * 上面执行的，仅仅是程序员通过add到spring容器中的beanFactoryPostProcessor
		 *
		 * 在对包进行扫描的时候，会把我们定义的通过@Component注入的beanFactoryPostProcessor的实现类，作为beanDefinition注入，然后在这里进行处理：也即：当spring执行到这里的时候，已经将bean转换成了beanDefinition，且存到了BeanDefinitionMaps中
		 */
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		/**
		 * BeanFactoryPostProcess的实现类，在执行的时候，也是有优先级的
		 * PriorityOrdered  --> Ordered --> other
		 */
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			//如果当前beanFactoryPostProcessor已经被执行过了，就跳过，无需再次执行
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		/**
		 * 1.获取到所有实现了beanPostProcessor接口的实现类的name,从beanDefinitionMaps中获取
		 *  这里在从BeanDefinitionMap中获取BeanPostProcessor类型的实现类的时候，会进行bean的合并，也即：将bean转换为RootBeanDefinition
		 */
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		/**
		 * 2.internalPostProcessors:存储的是实现了MergedBeanDefinitionPostProcessor接口的实现类
		 * 这里没想明白为什么要把实现了该接口的实现类单独存储
		 */
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		/**
		 * 3.按照后置处理器实现的ordered接口 分别存到不同的集合中
		 */
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				/**
				 * 4.在调用getBean的时候，就会调用对应的初始化方法，完成对beanPostProcessor的初始化
				 */
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		/**
		 * 5.registerBeanPostProcessors:该方法，就是把入参中的beanPostProcessor实现类存入到beanPostProcessors这个list集合中
		 */
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			//在通过getBean()方法获取orderedPostProcessorNames中beanPostProcessor对象的时候，会对beanPostProcessor进行初始化
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		/**
		 * 这里为什么要对mergeBeanDefinitionPostProcessor的beanPostProcessor进行重新一遍的处理？
		 */
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
