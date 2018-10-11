import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This is several solutions to a common guice scenario in which you need several implementations of the same
 * interface to exist at once. Guice's documentation calls this the robot legs problem in which a robot has two
 * equal legs but needs two different sided feet.
 * <p>
 * In this case, we are implementing an emotional reaction processor where you need to be able to respond in both
 * a happy and a sad way.
 */
public class GuiceMultiImpl {
    public interface Handler {
        void handle();
    }

    static class HappyHandler implements Handler {
        @Override
        public void handle() {
            System.out.println(this.getClass().getName() + " - Yay");
        }
    }

    static class SadHandler implements Handler {
        @Override
        public void handle() {
            System.out.println(this.getClass().getName() + " - Boo!");
        }
    }

    static class ReactionProcessor {
        private final Handler emotionHandler;

        @Inject
        ReactionProcessor(Handler handler) {
            this.emotionHandler = handler;
        }

        void react() {
            emotionHandler.handle();
        }
    }


    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    @interface Sad {
    }

    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    @interface Happy {
    }


    public static void main(String args[]) {
        System.out.println("Using Provider");
        provider();

        System.out.println("\nUsing Private Module");
        privateModule();

        System.out.println("\nUsing Private Module v2");
        privateModulev2();
    }

    static class SimpleReactionModule extends AbstractModule {
        @Override
        public void configure() {
            bind(Handler.class).to(SadHandler.class);
        }
    }

    private static void singleBind() {
        Injector i = Guice.createInjector(new SimpleReactionModule());
        final ReactionProcessor alwaysSad = i.getInstance(ReactionProcessor.class);
        alwaysSad.react();
    }

    /**
     * If the class being injected is simple enough, it can be fine to just manually create it in a provider
     * like is done in this module.
     * <p>
     * In this case, we create the ReactionProcessor manually, so it never actually gets injected. In this simple case
     * this is not bad, but it does get a little bit repetitive with the attribute specification.
     */
    static class ProviderReactionModule extends AbstractModule {
        @Provides
        @Sad
        @Singleton
        ReactionProcessor getSadProcessor(@Sad Handler h) {
            return new ReactionProcessor(h);
        }

        @Provides
        @Happy
        @Singleton
        ReactionProcessor getHappyProcessor(@Happy Handler h) {
            return new ReactionProcessor(h);
        }

        @Override
        public void configure() {
            bind(Handler.class).annotatedWith(Sad.class).to(SadHandler.class);
            bind(Handler.class).annotatedWith(Happy.class).to(HappyHandler.class);
        }
    }

    private static void provider() {
        // Provider reaction module works by binding the labeled version
        Injector i2 = Guice.createInjector(new ProviderReactionModule());
        final ReactionProcessor sad = i2.getInstance(Key.get(ReactionProcessor.class, Sad.class));
        final ReactionProcessor happy = i2.getInstance(Key.get(ReactionProcessor.class, Happy.class));

        sad.react();
        happy.react();
    }

    /**
      * This is the way that the Guice documentation solves this problem. Its probably the most flexible since it gives
      * the caller the most freedom to decide how to bind the handler.
      */

    static abstract class PrivateReactorModule extends PrivateModule {
        private final Class<? extends Annotation> annotation;

        PrivateReactorModule(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        @Override
        protected void configure() {
            bind(ReactionProcessor.class).annotatedWith(annotation).to(ReactionProcessor.class);
            expose(ReactionProcessor.class).annotatedWith(annotation);

            bindHandler();
        }

        abstract void bindHandler();
    }

    private static void privateModule() {
        // Private module solution. Can also pass the desired Handler class as a parameter.
        Injector i3 = Guice.createInjector(
            new PrivateReactorModule(Sad.class) {
                @Override
                void bindHandler() {
                    bind(Handler.class).to(SadHandler.class);
                }
            },
            new PrivateReactorModule(Happy.class) {
                @Override
                void bindHandler() {
                    bind(Handler.class).to(HappyHandler.class);
                }
            });

        final ReactionProcessor sad1 = i3.getInstance(Key.get(ReactionProcessor.class, Sad.class));
        final ReactionProcessor happy1 = i3.getInstance(Key.get(ReactionProcessor.class, Happy.class));

        sad1.react();
        happy1.react();
    }

    /**
     * In this we have a slight variant of the private module solution in which we know that the handler binding
     * will always be a simple bind call. In exchange for a second param in the private module, we get a much cleaner
     * call to createInjector.
     */
    static class PrivateReactorModulev2 extends PrivateModule {
        private final Class<? extends Annotation> annotation;
        private final Class<? extends Handler> handler;

        PrivateReactorModulev2(Class<? extends Annotation> annotation,
                               Class<? extends Handler> handler) {
            this.annotation = annotation;
            this.handler = handler;
        }

        @Override
        protected void configure() {
            bind(Handler.class).to(handler);
            bind(ReactionProcessor.class).annotatedWith(annotation).to(ReactionProcessor.class);
            expose(ReactionProcessor.class).annotatedWith(annotation);
        }
    }

    private static void privateModulev2() {
        // Private module solution. Can also pass the desired Handler class as a parameter.
        Injector i3 = Guice.createInjector(
            new PrivateReactorModulev2(Sad.class, SadHandler.class),
            new PrivateReactorModulev2(Happy.class, HappyHandler.class));

        final ReactionProcessor sad1 = i3.getInstance(Key.get(ReactionProcessor.class, Sad.class));
        final ReactionProcessor happy1 = i3.getInstance(Key.get(ReactionProcessor.class, Happy.class));

        sad1.react();
        happy1.react();
    }
}
