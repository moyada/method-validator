# Medivh

[![Build Status](https://travis-ci.org/moyada/medivh.svg?branch=master)](https://travis-ci.org/moyada/medivh)
![version](https://img.shields.io/badge/java-%3E%3D6-red.svg)
![java lifecycle](https://img.shields.io/badge/java%20lifecycle-compile-yellow.svg)
[![Maven Central](https://img.shields.io/badge/maven%20central-1.2.0-brightgreen.svg)](https://search.maven.org/search?q=g:%22io.github.moyada%22%20AND%20a:%22medivh%22)
[![license](https://img.shields.io/hexpm/l/plug.svg)](https://github.com/moyada/medivh/blob/master/LICENSE)

English | [简体中文](README_CN.md)

Medivh is A Java annotation processor for automatically generate method parameter verify.

## Motivation

We often need to check the method parameters, especially in remote invocation.
This tool can save time and effort in this respect, modify the syntax tree at `compile time` by configure annotations, to insert parameter verification logic into method.

## Features

* Support null check for Object type.

* Support size check for byte, short, int, long, float and double.

* Support blank string check.

* Support length check for String and array.

* Support capacity check for Collection and Map.

* Can choose to throw an exception or return data when the verification fails.

## Requirements

JDK 1.6 or higher.

JDK 1.8 or higher if verify parameter is an interface. 

## Quick start

### 1. Adding dependencies to your project 

Using Maven

```
<dependencies>
    <dependency>
        <groupId>io.github.moyada</groupId>
        <artifactId>medivh</artifactId>
        <version>1.2.0</version>
        <scope>provided</scope>
    </dependency>
<dependencies/>
```

Using Gradle

```
dependencies {
  compileOnly 'io.github.moyada:medivh:1.2.0'
  // before 2.12 version
  // provided 'io.github.moyada:medivh:1.2.0'
}
```

Without build tool, you can download last jar from 
[![release](https://img.shields.io/badge/release-v1.2.0-blue.svg)](https://github.com/moyada/medivh/releases/latest) 
or
[![Maven Central](https://img.shields.io/maven-central/v/io.github.moyada/medivh.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.moyada%22%20AND%20a:%22medivh%22)
.

### 2. Configure annotation in you program

* A use example is _[here](#example)_ and _[Wiki](https://github.com/moyada/medivh/wiki)_.

Annotation usage

| Annotation | Action Scope | Effect |
| :---- | :----- | :---- |
| @NotNull | field, parameter-less method, method parameter | provide null-check for object type, it is turned on when any rules are used. |
| @Nullable | field, parameter-less method, method parameter | do not null-check. |
| @NotBlank | field, parameter-less method, method parameter | provide blank string check for data of String type. |
| @SizeRule | field, parameter-less method, method parameter | provide size or length verify for String, array, collection and map type. |
| @NumberRule | field, parameter-less method, method parameter | provide size verify for basic numeric type (such as int and Integer). |
| @Throw | class, non-abstract method, method parameter | define thrown exception when method parameters is invalid. |
| @Return | non-abstract method, method parameter | define return data when method parameters is invalid, support null, object and basic type. |
| @Exclusive | non-abstract method, method parameter | disable verification logic. |
| @Variable | non-abstract method, class | modify the name of the variable or method generated by verification logic. |

Attribute description

| Attribute | Effect |
| :--- | :--- |
| NumberRule.min() | set the minimum allowed value. |
| NumberRule.max() | set the maximum allowed value. |
| SizeRule.min() | set the minimum allowed length or capacity. |
| SizeRule.max() | set the maximum allowed length or capacity. |
| Throw.value() | configure thrown exception, the exception type must have a String constructor, default is `IllegalArgumentException` . |
| Throw.message() | modify the message head of thrown exception. |
| Return.value() | set the return data, need to have a correspond constructor when return type is object. |
| Return.type() | configure the type of data returned, this type must be subclasses or implementation classes of the method return type. |

### 3. Compile project

Use compile commands of build tool, such as `mvn compile` or `gradle build`.
 
Or use java compile command, such as `javac -cp medivh.jar MyApp.java`.

After compilation phase, the verify logic will be generated.

#### Configuration option

| Property | Effect |
| :--- | :--- |
| medivh.method | configure the name of default validation method, default is `invalid0` . |
| medivh.var | configure the name of default local variable, default is `mvar_0` . |
| medivh.message | configure the default message head of exception, default is `Invalid input parameter` . |
| medivh.info.null | configure the default info of null verify, default is `is null` . |
| medivh.info.equals | configure the default info of equals verify, default is `cannot equals` . |
| medivh.info.less | configure the default info of less verify, default is `less than` . |
| medivh.info.great | configure the default info of great verify, default is `great than` . |
| medivh.info.blank | configure the default info of blank verify, default is `is blank` . |
| medivh.method.blank | define the method for verify blank，format is `<package>.<className>.<methodName>` , not specified will create  `io.moyada.medivh.support.Util` to provide method. |

## Example

_[More use cases](https://github.com/moyada/medivh/wiki)_

```
public class MyApp {

    @Throw
    public Info run(Args args,
                    @Nullable Info info,
                    @Return({"test", "0"}) @NotBlank String name,
                    @Return("null") @NumberRule(min = "1") int num) {
        // process
        return new Info();
    }

    class Args {

        @NumberRule(max = "1000") int id;

        @NotNull HashMap<String, Object> param;

        @Nullable @SizeRule(min = 5) boolean[] value;
    }

    static class Info {

        @SizeRule(min = 50) String name;

        @Nullable @NumberRule(min = "-25.02", max = "200") Double price;

        @SizeRule(min = 10, max = 10) List<String> extra;

        public Info() {
        }

        Info(String name, Double price) {
            this.name = name;
            this.price = price;
        }
    }
}
```

As the example code, the compiled content will be:

```
public class MyApp {
    public MyApp() {
    }

    public MyApp.Info run(MyApp.Args args, MyApp.Info info, String name, int num) {
        if (args == null) {
            throw new IllegalArgumentException("Invalid input parameter, cause args is null");
        } else {
            String mvar_0 = args.invalid0();
            if (mvar_0 != null) {
                throw new IllegalArgumentException("Invalid input parameter, cause " + mvar_0);
            } else {
                if (info != null) {
                    mvar_0 = info.invalid0();
                    if (mvar_0 != null) {
                        throw new IllegalArgumentException("Invalid input parameter, cause " + mvar_0);
                    }
                }

                if (name == null) {
                    return new MyApp.Info("test", 0.0D);
                } else if (Util.isBlank(name)) {
                    return new MyApp.Info("test", 0.0D);
                } else {
                    return num < 1 ? null : new MyApp.Info();
                }
            }
        }
    }

    static class Info {
        String name;
        Double price;
        List<String> extra;

        public Info() {
        }

        Info(String name, Double price) {
            this.name = name;
            this.price = price;
        }

        public String invalid0() {
            if (this.name == null) {
                return "name is null";
            } else if (this.name.length() < 50) {
                return "name.length() less than 50";
            } else if (this.extra == null) {
                return "extra is null";
            } else if (this.extra.size() != 10) {
                return "extra cannot equals 10";
            } else {
                return this.price != null && this.price > 200.0D ? "price great than 200.0" : null;
            }
        }
    }

    class Args {
        int id;
        HashMap<String, Object> param;
        boolean[] value;

        Args() {
        }

        public String invalid0() {
            if (this.param == null) {
                return "param is null";
            } else if (this.value != null && this.value.length < 5) {
                return "value.length less than 5";
            } else {
                return this.id > 1000 ? "id great than 1000" : null;
            }
        }
    }
}
``` 