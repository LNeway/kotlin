// !DIAGNOSTICS: -UNREACHABLE_CODE -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT

// MODULE: libModule
// FILE: libModule/JavaClass.java
package libModule;

public class JavaClass {
    public static final boolean FALSE = false;
    public static int obj = 5;
}


// MODULE: mainModule(libModule)
// FILE: KotlinClass.kt
package mainModule
import libModule.*


// TESTCASE NUMBER: 1
fun case1() {
    val res = JavaClass.FALSE<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}

// TESTCASE NUMBER: 2
fun case2() {
    val x = JavaClass.obj<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}

// TESTCASE NUMBER: 3
fun case3() {
    val a = false
    val x = a<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}

// TESTCASE NUMBER: 4
fun case4() {
    val x = "weds"<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}

// TESTCASE NUMBER: 5
fun case5(nothing: Nothing) {
    val y = nothing<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>
}
