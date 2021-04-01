package io.tolgee.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RepositoryRepository : JpaRepository<io.tolgee.model.Repository, Long> {
    @Query("""from Repository r 
        left join fetch Permission p on p.repository = r and p.user.id = :userAccountId
        left join fetch Organization o on r.organizationOwner = o
        left join fetch OrganizationRole role on role.organization = o and role.user.id = :userAccountId
        where p is not null or (role is not null)
        """)
    fun findAllPermitted(userAccountId: Long): List<Array<Any>>

    fun findAllByOrganizationOwnerId(organizationOwnerId: Long): List<io.tolgee.model.Repository>

    fun findAllByOrganizationOwnerId(organizationOwnerId: Long, pageable: Pageable): Page<io.tolgee.model.Repository>
}
