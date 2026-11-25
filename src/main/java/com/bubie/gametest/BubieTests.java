package com.bubie.gametest;

import com.bubie.Bubie;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Turns every {@link BubieTest} method into a test function and a test instance, named
 * {@code bubie:<class>.<method>} in lower case (as the old annotation-based tests were).
 */
public final class BubieTests {
    static final List<Class<?>> CLASSES = List.of(AreaGameTests.class, BubieGameTests.class, BuilderGameTests.class,
            DjGameTests.class, GuardGameTests.class, PackCompatGameTests.class, PerfGameTests.class, TeamGameTests.class,
            TrackerGameTests.class);

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Bubie.MOD_ID);

    private record Entry(Identifier id, BubieTest test) {
    }

    private static final List<Entry> TESTS = new ArrayList<>();

    private BubieTests() {
    }

    public static void register(IEventBus modBus) {
        for (Class<?> type : CLASSES) {
            for (Method method : type.getDeclaredMethods()) {
                BubieTest test = method.getAnnotation(BubieTest.class);
                if (test == null) continue;
                if (!Modifier.isStatic(method.getModifiers())) throw new IllegalStateException(method + " must be static");
                method.setAccessible(true);
                String name = (type.getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT);
                FUNCTIONS.register(name, () -> helper -> invoke(method, helper));
                TESTS.add(new Entry(Identifier.fromNamespaceAndPath(Bubie.MOD_ID, name), test));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(BubieTests::onRegisterTests);
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Bubie.MOD_ID, "default"), new TestEnvironmentDefinition.AllOf());
        for (Entry e : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, e.id());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment,
                    Identifier.fromNamespaceAndPath(Bubie.MOD_ID, e.test().template()), e.test().timeoutTicks(),
                    e.test().setupTicks(), e.test().required());
            event.registerTest(e.id(), new FunctionGameTestInstance(function, data));
        }
    }
}
