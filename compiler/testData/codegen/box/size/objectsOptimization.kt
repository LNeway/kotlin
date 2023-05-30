// TARGET_BACKEND: WASM

// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm 15_936
// WASM_DCE_EXPECTED_OUTPUT_SIZE:  mjs 5_450

object Simple

object SimpleWithPureProperty {
    val text = "Hello"
}

object SimpleWithPropertyInitializedDurintInit {
    val text: String
    init {
        text = "Hello"
    }
}

object SimpleWithFunctionsOnly {
    fun foo() = "Foo"
    fun bar() = "Bar"
}

interface Callable {
    fun call(): String
}

object SimpleWithInterface : Callable {
    override fun call() = "OK"
}

object UsedGetFieldInside {
    val anotherText = SimpleWithPureProperty.text
}

fun box(): String {
    if (SimpleWithPureProperty.text != "Hello") return "Fail simple case with pure property"
    if (SimpleWithPropertyInitializedDurintInit.text != "Hello") return "Fail simple case with pure property initialized inside init block"
    if (SimpleWithFunctionsOnly.foo() != "Foo" || SimpleWithFunctionsOnly.bar() != "Bar") return "Fail simple case with functions only"
    if (SimpleWithInterface.call() != "OK") return "Fail simple case with interface implementing"
    if (UsedGetFieldInside.anotherText != "Hello") return "Fail object which used another object inside its initialization block"
    return "OK"
}
