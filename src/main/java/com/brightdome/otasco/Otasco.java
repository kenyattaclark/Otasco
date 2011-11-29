package com.brightdome.otasco;

import org.apache.commons.lang.StringUtils;
import java.lang.reflect.Field;
import java.util.List;
import fj.F;
import fj.data.Option;
import java.util.LinkedList;

/**
 * <ul>
 * <li>Allows testing class under test without creating useless setters for dependencies.</li>
 * <li>Makes the test class more readable.</li>
 * </ul>
 *
 * <pre>
 *   public class InvoiceManagerTest {
 *
 *       &#064;Dependency
 *       private InvoiceCalculator invoiceCalculator;
 *
 *       &#064;Dependency
 *       private InvoiceDao invoiceDao;
 *
 *     	 &#064;ClassUnderTest
 *       private InvoiceManager manager;
 *
 *       &#064;Before public void setup() {
 *           invoiceCalculator = new InvoiceCalculator();
 *           invoiceDao = new InvoiceDao();
 *           manager = new InvoiceManager();
 *           Otasco.init(this);
 *       }
 *   }
 *
 * </pre>
 *
 * <b><code>Otasco.init(this)</code></b> method has to called to "wire" the annotated instance of class under test to the annotated dependencies.
 */
public class Otasco {
    

    private Otasco() {}

    /**
     * Wires objects annotated with &#064;ClassUnderTest for given testClass with objects annotated with &#064;Dependency.
     * <p>
     * See examples in javadoc for {@link Otasco} class.
     *
     * @param testClass
     */
    public static void init(final Object testClass) {
        if (testClass == null) {
            throw new OtascoException("testClass cannot be null. For info on how to use @ClassUnderTest and @Dependency see examples in Javadoc for OtascoAnnotations class.");
        }

        final List<Field> dependencyFields = dependencyFieldsFromAnnotations(testClass);

        final Option<Field> maybeClassUnderTest = classUnderTestFromAnnotation(testClass);
        if (maybeClassUnderTest.isNone()) {
            throw new OtascoException("@ClassUnderTest must be specified.  For info on how to use @ClassUnderTest and @Dependency see examples in Javadoc for OtascoAnnotations class.");
        }
        
        final Field classUnderTestField = maybeClassUnderTest.some();

        makeAccessible(classUnderTestField);

        try {
            final Object classUnderTest = classUnderTestField.get(testClass);

            if (classUnderTest == null) {
                throw new OtascoException("@ClassUnderTest must be initialize prior to calling OtascoAnnotations.init().  For info on how to use @ClassUnderTest and @Dependency see examples in Javadoc for OtascoAnnotations class.");
            }

            for (Field testField : dependencyFields) {
                final Field dependencyField = classUnderTest.getClass().getDeclaredField(dependencyFieldName(testField));
                makeAccessible(dependencyField);
                makeAccessible(testField);
                dependencyField.set(classUnderTest, testField.get(testClass));
            }
        } catch (Exception e) {
            throw new OtascoException("Error processing class under test: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts to set the named field on a target object with the value passed in.
     * @param name
     * @param value
     * @param target
     */
    public static void setField(final String name, final Object value, final Object target) {
        try {
            final Field field = target.getClass().getField(name);
            makeAccessible(field);
            field.set(target, value);
        } catch (Exception e) {
            throw new OtascoException("Error setting field on class: " + e.getMessage(), e);
        }
    }

    private static void makeAccessible(final Field cutField) {
        cutField.setAccessible(true);
    }
    
    private static fj.data.List<Field> declaredFields(final Object testClass) { 
        return fj.data.List.<Field>list(testClass.getClass().getDeclaredFields());
    }
    
    private static Option<Field> classUnderTestFromAnnotation(final Object testClass) {
        return declaredFields(testClass).find(new F<Field, Boolean>(){

            @Override
            public Boolean f(Field field) {
                return field.getAnnotation(ClassUnderTest.class) != null;
            }
        });
    }

    private static List<Field> dependencyFieldsFromAnnotations(final Object testClass) {
        return new LinkedList<Field>(declaredFields(testClass).filter(new F<Field, Boolean>() {

            @Override
            public Boolean f(Field field) {
                return field.getAnnotation(Dependency.class) != null;
            }
        }).toCollection());
    }

    private static String dependencyFieldName(Field testField) {
        final String dependencyValue = testField.getAnnotation(Dependency.class).value();
        return StringUtils.isBlank(dependencyValue) ? testField.getName() : dependencyValue;
    }

}