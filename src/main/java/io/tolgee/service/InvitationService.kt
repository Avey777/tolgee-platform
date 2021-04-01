package io.tolgee.service

import io.tolgee.constants.Message
import io.tolgee.exceptions.BadRequestException
import io.tolgee.model.Invitation
import io.tolgee.model.Organization
import io.tolgee.model.Permission.RepositoryPermissionType
import io.tolgee.model.Repository
import io.tolgee.model.UserAccount
import io.tolgee.model.enums.OrganizationRoleType
import io.tolgee.repository.InvitationRepository
import io.tolgee.security.AuthenticationFacade
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
open class InvitationService @Autowired constructor(
        private val invitationRepository: InvitationRepository,
        private val authenticationFacade: AuthenticationFacade,
        private val organizationRoleService: OrganizationRoleService,
        private val permissionService: PermissionService
) {
    @Transactional
    open fun create(repository: Repository, type: RepositoryPermissionType): String {
        val code = RandomStringUtils.randomAlphabetic(50)
        val invitation = Invitation(null, code)
        invitation.permission = permissionService.createForInvitation(invitation, repository, type)
        invitationRepository.save(invitation)
        return code
    }

    @Transactional
    open fun create(organization: Organization, type: OrganizationRoleType): Invitation {
        val code = RandomStringUtils.randomAlphabetic(50)
        val invitation = Invitation(null, code)
        invitation.organizationRole = organizationRoleService.createForInvitation(invitation, type, organization)
        invitationRepository.save(invitation)
        return invitation
    }

    @Transactional
    open fun removeExpired() {
        invitationRepository.deleteAllByCreatedAtLessThan(Date.from(Instant.now().minus(Duration.ofDays(30))))
    }

    @Transactional
    open fun accept(code: String?) {
        this.accept(code, authenticationFacade.userAccount)
    }

    @Transactional
    open fun accept(code: String?, userAccount: UserAccount) {
        val invitation = getInvitation(code)
        val permission = invitation.permission
        val organizationRole = invitation.organizationRole

        if (!(permission == null).xor(organizationRole == null)) {
            throw IllegalStateException("Exactly of permission and organizationRole may be set")
        }

        permission?.let {
            if (permissionService.findOneByRepositoryIdAndUserId(permission.repository!!.id, userAccount.id!!) != null) {
                throw BadRequestException(Message.USER_ALREADY_HAS_PERMISSIONS)
            }
            permissionService.acceptInvitation(permission, userAccount)
        }

        organizationRole?.let {
            if (organizationRoleService.isUserMemberOrOwner(userAccount.id!!, it.id!!)) {
                throw BadRequestException(Message.USER_ALREADY_HAS_ROLE)
            }
            organizationRoleService.acceptInvitation(organizationRole, userAccount)
        }

        //avoid cascade delete
        invitation.permission = null
        invitation.organizationRole = null
        invitationRepository.delete(invitation)
    }

    open fun getInvitation(code: String?): Invitation {
        return invitationRepository.findOneByCode(code).orElseThrow { //this exception is important for sign up service! Do not remove!!
            BadRequestException(Message.INVITATION_CODE_DOES_NOT_EXIST_OR_EXPIRED)
        }!!
    }

    open fun findById(id: Long): Optional<Invitation> {
        @Suppress("UNCHECKED_CAST")
        return invitationRepository.findById(id) as Optional<Invitation>
    }

    open fun getForRepository(repository: Repository): Set<Invitation> {
        return invitationRepository.findAllByPermissionRepositoryOrderByCreatedAt(repository)
    }

    @Transactional
    open fun delete(invitation: Invitation) {
        invitation.permission?.let {
            permissionService.delete(it)
        }
        if (invitation.organizationRole != null) {
            organizationRoleService
        }
        invitationRepository.delete(invitation)
    }

    fun getForOrganization(organization: Organization): List<Invitation> {
        return invitationRepository.getAllByOrganizationRoleOrganizationOrderByCreatedAt(organization)
    }
}
