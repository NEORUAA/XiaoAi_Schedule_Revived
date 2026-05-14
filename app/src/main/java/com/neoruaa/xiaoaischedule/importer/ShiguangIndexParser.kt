package com.neoruaa.xiaoaischedule.importer

import java.io.ByteArrayOutputStream

object ShiguangIndexParser {
    fun parse(bytes: ByteArray): List<ShiguangSchool> {
        val reader = ProtoReader(bytes)
        val result = mutableListOf<ShiguangSchool>()
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            if (tag == 0) break
            when (tag ushr 3) {
                3 -> result += parseSchool(reader.readLengthDelimited())
                else -> reader.skip(tag)
            }
        }
        return result
    }

    fun encodeForTest(schools: List<ShiguangSchool>): ByteArray {
        val out = ByteArrayOutputStream()
        schools.forEach { school ->
            out.writeField(3, encodeSchool(school))
        }
        return out.toByteArray()
    }

    private fun parseSchool(bytes: ByteArray): ShiguangSchool {
        val reader = ProtoReader(bytes)
        var id = ""
        var name = ""
        var initial = ""
        var resourceFolder = ""
        val adapters = mutableListOf<ShiguangAdapter>()
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            if (tag == 0) break
            when (tag ushr 3) {
                1 -> id = reader.readLengthDelimited().decode()
                2 -> name = reader.readLengthDelimited().decode()
                3 -> initial = reader.readLengthDelimited().decode()
                4 -> resourceFolder = reader.readLengthDelimited().decode()
                5 -> adapters += parseAdapter(reader.readLengthDelimited())
                else -> reader.skip(tag)
            }
        }
        return ShiguangSchool(
            id = id,
            name = name,
            initial = initial.ifBlank { "#" },
            resourceFolder = resourceFolder,
            adapters = adapters,
        )
    }

    private fun parseAdapter(bytes: ByteArray): ShiguangAdapter {
        val reader = ProtoReader(bytes)
        var adapterId = ""
        var adapterName = ""
        var importType = 0
        var assetJsPath = ""
        var importUrl = ""
        var description = ""
        var maintainer = ""
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            if (tag == 0) break
            when (tag ushr 3) {
                1 -> adapterId = reader.readLengthDelimited().decode()
                2 -> adapterName = reader.readLengthDelimited().decode()
                3 -> importType = reader.readVarint().toInt()
                4 -> assetJsPath = reader.readLengthDelimited().decode()
                5 -> importUrl = reader.readLengthDelimited().decode()
                6 -> description = reader.readLengthDelimited().decode()
                7 -> maintainer = reader.readLengthDelimited().decode()
                else -> reader.skip(tag)
            }
        }
        return ShiguangAdapter(
            adapterId = adapterId,
            adapterName = adapterName,
            importType = importType,
            assetJsPath = assetJsPath,
            importUrl = importUrl,
            description = description,
            maintainer = maintainer,
        )
    }

    private fun encodeSchool(school: ShiguangSchool): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeField(1, school.id.toByteArray())
        out.writeField(2, school.name.toByteArray())
        out.writeField(3, school.initial.toByteArray())
        out.writeField(4, school.resourceFolder.toByteArray())
        school.adapters.forEach { out.writeField(5, encodeAdapter(it)) }
        return out.toByteArray()
    }

    private fun encodeAdapter(adapter: ShiguangAdapter): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeField(1, adapter.adapterId.toByteArray())
        out.writeField(2, adapter.adapterName.toByteArray())
        out.writeVarintField(3, adapter.importType.toLong())
        out.writeField(4, adapter.assetJsPath.toByteArray())
        out.writeField(5, adapter.importUrl.toByteArray())
        out.writeField(6, adapter.description.toByteArray())
        out.writeField(7, adapter.maintainer.toByteArray())
        return out.toByteArray()
    }

    private fun ByteArray.decode(): String = toString(Charsets.UTF_8)

    private fun ByteArrayOutputStream.writeField(number: Int, value: ByteArray) {
        writeVarint(((number shl 3) or 2).toLong())
        writeVarint(value.size.toLong())
        write(value)
    }

    private fun ByteArrayOutputStream.writeVarintField(number: Int, value: Long) {
        writeVarint(((number shl 3) or 0).toLong())
        writeVarint(value)
    }

    private fun ByteArrayOutputStream.writeVarint(value: Long) {
        var current = value
        while (true) {
            if ((current and 0x7f.inv().toLong()) == 0L) {
                write(current.toInt())
                return
            }
            write(((current and 0x7f) or 0x80).toInt())
            current = current ushr 7
        }
    }
}

private class ProtoReader(private val bytes: ByteArray) {
    private var position = 0

    fun exhausted(): Boolean = position >= bytes.size

    fun readTag(): Int {
        if (exhausted()) return 0
        return readVarint().toInt()
    }

    fun readVarint(): Long {
        var shift = 0
        var result = 0L
        while (position < bytes.size && shift < 64) {
            val b = bytes[position++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        return result
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarint().toInt().coerceAtLeast(0)
        val end = (position + length).coerceAtMost(bytes.size)
        return bytes.copyOfRange(position, end).also { position = end }
    }

    fun skip(tag: Int) {
        val wireType = tag and 0x7
        when (wireType) {
            0 -> readVarint()
            1 -> position = (position + 8).coerceAtMost(bytes.size)
            2 -> readLengthDelimited()
            5 -> position = (position + 4).coerceAtMost(bytes.size)
            else -> position = bytes.size
        }
    }
}
