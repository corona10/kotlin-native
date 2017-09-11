/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.konan.ir

import org.jetbrains.kotlin.backend.common.atMostOne
import org.jetbrains.kotlin.backend.common.ir.Ir
import org.jetbrains.kotlin.backend.common.ir.Symbols
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.ValueType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrCallWithIndexedArgumentsBase
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType
import kotlin.properties.Delegates

// This is what Context collects about IR.
internal class KonanIr(context: Context, irModule: IrModuleFragment): Ir<Context>(context, irModule) {

    val propertiesWithBackingFields = mutableSetOf<PropertyDescriptor>()

    val originalModuleIndex = ModuleIndex(irModule)

    lateinit var moduleIndexForCodegen: ModuleIndex

    override var symbols: KonanSymbols by Delegates.notNull()
}

internal class KonanSymbols(context: Context, val symbolTable: SymbolTable): Symbols<Context>(context, symbolTable) {

    val interopNativePointedGetRawPointer =
            symbolTable.referenceSimpleFunction(context.interopBuiltIns.nativePointedGetRawPointer)

    val interopCPointerGetRawValue = symbolTable.referenceSimpleFunction(context.interopBuiltIns.cPointerGetRawValue)

    val getNativeNullPtr = symbolTable.referenceSimpleFunction(context.builtIns.getNativeNullPtr)

    val boxFunctions = ValueType.values().associate {
        val boxFunctionName = "box${it.classFqName.shortName()}"
        it to symbolTable.referenceSimpleFunction(context.getInternalFunctions(boxFunctionName).single())
    }

    val boxClasses = ValueType.values().associate {
        it to symbolTable.referenceClass(context.getInternalClass("${it.classFqName.shortName()}Box"))
    }

    val unboxFunctions = ValueType.values().mapNotNull {
        val unboxFunctionName = "unbox${it.classFqName.shortName()}"
        context.getInternalFunctions(unboxFunctionName).atMostOne()?.let { descriptor ->
            it to symbolTable.referenceSimpleFunction(descriptor)
        }
    }.toMap()


    val scheduleImpl = symbolTable.referenceSimpleFunction(context.interopBuiltIns.scheduleImplFunction)
}

internal interface IrPrivateFunctionCall : IrCall {
    val moduleName: String
    val totalFunctions: Int
    val functionIndex: Int
}

internal class IrPrivateFunctionCallImpl(startOffset: Int,
                                         endOffset: Int,
                                         type: KotlinType,
                                         override val symbol: IrFunctionSymbol,
                                         override val descriptor: FunctionDescriptor,
                                         typeArguments: Map<TypeParameterDescriptor, KotlinType>?,
                                         override val moduleName: String,
                                         override val totalFunctions: Int,
                                         override val functionIndex: Int
) : IrPrivateFunctionCall,
        IrCallWithIndexedArgumentsBase(startOffset, endOffset, type, symbol.descriptor.valueParameters.size, typeArguments) {

    override val superQualifierSymbol: IrClassSymbol?
        get() = null

    override val superQualifier: ClassDescriptor?
        get() = null

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitCall(this, data)

}