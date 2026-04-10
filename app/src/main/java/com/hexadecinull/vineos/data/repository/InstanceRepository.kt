package com.hexadecinull.vineos.data.repository

import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstanceRepository @Inject constructor(
    private val dao: VMInstanceDao,
) {
    /** Live stream of all instances, ordered by most recently used. */
    fun observeAll(): Flow<List<VMInstance>> = dao.observeAll()

    suspend fun getById(id: String): VMInstance? = dao.getById(id)

    suspend fun save(instance: VMInstance) = dao.insert(instance)

    suspend fun update(instance: VMInstance) = dao.update(instance)

    suspend fun updateStatus(id: String, status: VMStatus) = dao.updateStatus(id, status)

    suspend fun touchLastUsed(id: String) = dao.updateLastUsed(id)

    suspend fun delete(instance: VMInstance) = dao.delete(instance)

    suspend fun deleteById(id: String) = dao.deleteById(id)
}
