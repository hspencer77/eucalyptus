/*************************************************************************
 * Copyright 2009-2013 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.auth;

import static com.eucalyptus.auth.policy.PolicySpec.*;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.eucalyptus.auth.api.PolicyEngine;
import com.eucalyptus.auth.policy.PolicySpec;
import com.eucalyptus.auth.principal.AccessKey;
import com.eucalyptus.auth.principal.Account;
import com.eucalyptus.auth.principal.Certificate;
import com.eucalyptus.auth.principal.Group;
import com.eucalyptus.auth.principal.InstanceProfile;
import com.eucalyptus.auth.principal.Policy;
import com.eucalyptus.auth.principal.Role;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.auth.util.X509CertHelper;
import com.eucalyptus.crypto.Certs;
import com.eucalyptus.crypto.Crypto;
import com.eucalyptus.crypto.util.B64;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Privileged {

  public static Account signupAccount( String accountName, String password, String email ) throws AuthException {
    return doCreateAccount( accountName, password, email, false );
  }

  public static Account createAccount( User requestUser, String accountName, String password, String email ) throws AuthException {
    if ( !requestUser.isSystemUser() ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, "", null, IAM_CREATEACCOUNT, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return doCreateAccount( accountName, password, email, true );
  }

  private static Account doCreateAccount( String accountName, String password, String email, boolean skipRegistration ) throws AuthException {
    Account newAccount = Accounts.addAccount( accountName );
    Map<String, String> info = null;
    if ( email != null ) {
      info = Maps.newHashMap( );
      info.put( User.EMAIL, email );
    }
    User admin = newAccount.addUser( User.ACCOUNT_ADMIN, "/", skipRegistration, true/*enabled*/, info );
    admin.resetToken( );
    admin.createConfirmationCode( );
    if ( password != null ) {
      admin.setPassword( Crypto.generateEncryptedPassword( password ) );
      admin.setPasswordExpires( System.currentTimeMillis( ) + User.PASSWORD_LIFETIME );
    }
    return newAccount;
  }
  
  public static void deleteAccount( User requestUser, Account account, boolean recursive ) throws AuthException {
    if ( !requestUser.isSystemUser() ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_DELETEACCOUNT, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    Accounts.deleteAccount( account.getName(), false/*forceDeleteSystem*/, recursive );
  }

  public static class RequestUserContext {
    private final String userId;
    private final String accountNumber;
    private final boolean accountAdmin;
    private final boolean systemAdmin;
    private final boolean systemUser;
    private final User user; // NOTE, do not invoke any methods on this without caching the result
    private Map<EvaluationContextKey,PolicyEngine.EvaluationContext> contexts = Maps.newHashMap( );

    private RequestUserContext( final User requestUser ) throws AuthException {
      final Account account = requestUser.getAccount( );
      this.userId = requestUser.getUserId( );
      this.accountNumber = account.getAccountNumber( );
      this.systemAdmin = requestUser.isSystemAdmin( );
      this.systemUser = requestUser.isSystemUser( );
      this.accountAdmin = requestUser.isAccountAdmin( );
      this.user = requestUser;
    }

    public String getUserId() {
      return userId;
    }

    public String getAccountNumber() {
      return accountNumber;
    }

    public boolean isAccountAdmin() {
      return accountAdmin;
    }

    public boolean isSystemAdmin() {
      return systemAdmin;
    }

    public boolean isSystemUser() {
      return systemUser;
    }

    public PolicyEngine.EvaluationContext evaluationContext(
        @Nonnull final String vendor,
        @Nonnull final String resource,
        @Nonnull final String action
    ) {
      final EvaluationContextKey key = new EvaluationContextKey( vendor, resource, action );
      PolicyEngine.EvaluationContext context = contexts.get( key );
      if ( context == null ) {
        context = Permissions.createEvaluationContext( vendor, resource, action, user );
        contexts.put( key, context );
      }
      return context;
    }

    @SuppressWarnings( "RedundantIfStatement" )
    private static final class EvaluationContextKey {
      @Nonnull private final String vendor;
      @Nonnull private final String resource;
      @Nonnull private final String action;

      private EvaluationContextKey(
          @Nonnull final String vendor,
          @Nonnull final String resource,
          @Nonnull final String action
      ) {
        this.vendor = vendor;
        this.resource = resource;
        this.action = action;
      }

      @Override
      public boolean equals( final Object o ) {
        if ( this == o ) return true;
        if ( o == null || getClass() != o.getClass() ) return false;

        final EvaluationContextKey that = (EvaluationContextKey) o;

        if ( !action.equals( that.action ) ) return false;
        if ( !resource.equals( that.resource ) ) return false;
        if ( !vendor.equals( that.vendor ) ) return false;

        return true;
      }

      @Override
      public int hashCode() {
        int result = vendor.hashCode();
        result = 31 * result + resource.hashCode();
        result = 31 * result + action.hashCode();
        return result;
      }
    }
  }
  
  public static RequestUserContext requestUser( final User requestUser ) throws AuthException {
    return new RequestUserContext( requestUser );  
  }
  
  public static boolean allowReadAccount( RequestUserContext requestUser, Account account ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, IAM_LISTACCOUNTS ),
            account.getAccountNumber( ),
            Accounts.getAccountFullName( account ) );
  }
  
  public static boolean allowListOrReadAccountPolicy( RequestUserContext requestUser, Account account ) throws AuthException {
    return requestUser.isSystemUser() &&
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, IAM_LISTACCOUNTPOLICIES ), account.getAccountNumber(), Accounts.getAccountFullName(account) ) &&
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, IAM_GETACCOUNTPOLICY ), account.getAccountNumber(), Accounts.getAccountFullName(account) );
  }
  
  public static void modifyAccount( User requestUser, Account account, String newName ) throws AuthException {
    if ( Account.SYSTEM_ACCOUNT.equals( account.getName( ) ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    try {
      Accounts.lookupAccountByName( newName );
      throw new AuthException( AuthException.CONFLICT );
    } catch ( AuthException ae ) {
      if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_CREATEACCOUNTALIAS, requestUser ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      }
      account.setName( newName );
    }
  }
  
  public static void deleteAccountAlias( User requestUser, Account account, String alias ) throws AuthException {
    if ( Account.SYSTEM_ACCOUNT.equals( account.getName( ) ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_DELETEACCOUNTALIAS, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( alias ) ) {
      throw new AuthException( AuthException.EMPTY_ACCOUNT_NAME );
    }
    // Only one alias is allowed by AWS IAM spec. Overwrite the current alias if matches.
    if ( account.getName( ).equals( alias ) ) {
      account.setName( account.getAccountNumber( ) );
    }
  }
  
  public static List<String> listAccountAliases( User requestUser, Account account ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_LISTACCOUNTALIASES, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    List<String> aliases = Lists.newArrayList( );
    aliases.add( account.getName( ) );
    return aliases;
  }
  
  public static Account getAccountSummary( User requestUser, Account account ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_GETACCOUNTSUMMARY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return account;
  }
  
  public static Group createGroup( User requestUser, Account account, String groupName, String path ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, "", account, IAM_CREATEGROUP, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Permissions.canAllocate( VENDOR_IAM, IAM_RESOURCE_GROUP, "", IAM_CREATEGROUP, requestUser, 1L ) ) {
      throw new AuthException( AuthException.QUOTA_EXCEEDED );
    }
    return account.addGroup( groupName, path );
  }
  
  public static boolean allowListGroup( RequestUserContext requestUser, Account account, Group group ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_GROUP, IAM_LISTGROUPS ),
            account.getAccountNumber( ),
            Accounts.getGroupFullName( group ) );
  }

  public static boolean allowReadGroup( User requestUser, Account account, Group group ) throws AuthException {
    return Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_GETGROUP, requestUser );
  }

  public static void deleteGroup( User requestUser, Account account, Group group, boolean recursive ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_DELETEGROUP, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    account.deleteGroup( group.getName( ), recursive );
  }
  
  public static void modifyGroup( User requestUser, Account account, Group group, String newName, String newPath ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_UPDATEGROUP, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Strings.isNullOrEmpty( newName ) ) {
      group.setName( newName );
    }
    if ( !Strings.isNullOrEmpty( newPath ) ) {
      group.setPath( newPath );
    }
  }

  public static User createUser( User requestUser, Account account, String userName, String path ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, "", account, IAM_CREATEUSER, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Permissions.canAllocate( VENDOR_IAM, IAM_RESOURCE_USER, "", IAM_CREATEUSER, requestUser, 1L ) ) {
      throw new AuthException( AuthException.QUOTA_EXCEEDED );
    }
    return account.addUser( userName, path, true, true, null );
  }
  
  public static boolean allowReadUser( User requestUser, Account account, User user ) throws AuthException {
    return requestUser.getUserId( ).equals( user.getUserId( ) ) || // user himself or ...
        Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_GETUSER, requestUser );
  }

  public static boolean allowListUser( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    return requestUser.getUserId( ).equals( user.getUserId( ) ) || // user himself or ...
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_LISTUSERS ),
            account.getAccountNumber( ),
            Accounts.getUserFullName( user ) );
  }

  public static boolean allowListAndReadUser( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    return requestUser.getUserId( ).equals( user.getUserId( ) ) || // user himself or ...
        ( Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_GETUSER ), account.getAccountNumber( ), Accounts.getUserFullName( user ) ) &&
          Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_LISTUSERS ), account.getAccountNumber( ), Accounts.getUserFullName( user ) ) );
  }

  public static void deleteUser( User requestUser, Account account, User user, boolean recursive ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_DELETEUSER, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( user.isAccountAdmin( ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    account.deleteUser( user.getName( ), false/*forceDeleteAdmin*/, recursive );
  }

  public static void modifyUser( User requestUser, Account account, User user, String newName, String newPath, Boolean enabled, Long passwordExpires, Map<String, String> info ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATEUSER, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Strings.isNullOrEmpty( newName ) ) {
      // Not allowed to modify admin user
      if ( user.isAccountAdmin( ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      }
      user.setName( newName );
    }
    if ( !Strings.isNullOrEmpty( newPath ) ) {
      // Not allowed to modify admin user
      if ( user.isAccountAdmin( ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      }
      user.setPath( newPath );
    }
    if ( enabled != null ) {
      // Not allowed to disable system admin (admin@eucalyptus) user
      // Only system user can disable account admin
      if ( user.isSystemAdmin( ) && user.isAccountAdmin( ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      } else if ( user.isAccountAdmin( ) && !requestUser.isSystemUser( ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );  
      }
      user.setEnabled( enabled );
    }
    if ( passwordExpires != null ) {
      if ( passwordExpires > new Date( ).getTime( ) ) {
        user.setPasswordExpires( passwordExpires );
      }
    }
    if ( info != null ) {
      user.setInfo( info );
    }
  }
  
  public static void updateUserInfoItem( User requestUser, Account account, User user, String key, String value ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATEUSER, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( value != null ) {
      user.setInfo( key, value );
    } else {
      user.removeInfo( key );
    }
  }
  
  public static void addUserToGroup( User requestUser, Account account, User user, Group group ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_ADDUSERTOGROUP, requestUser ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_ADDUSERTOGROUP, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( group.hasUser( user.getName( ) ) ) {
      throw new AuthException( AuthException.CONFLICT );
    }
    group.addUserByName( user.getName( ) );
  }
  
  public static void removeUserFromGroup( User requestUser, Account account, User user, Group group ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_REMOVEUSERFROMGROUP, requestUser ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_REMOVEUSERFROMGROUP, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !group.hasUser( user.getName( ) ) ) {
      throw new AuthException( AuthException.NO_SUCH_USER );
    }
    group.removeUserByName( user.getName( ) );
  }
  
  public static List<Group> listGroupsForUser( User requestUser, Account account, User user ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_LISTGROUPSFORUSER, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    List<Group> groups = Lists.newArrayList( );
    for ( Group g : user.getGroups( ) ) {
      if ( !g.isUserGroup( ) ) {
        groups.add( g );
      }
    }
    return groups;
  }

  public static Role createRole( User requestUser, Account account, String roleName, String path, String assumeRolePolicy ) throws AuthException, PolicyParseException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, "", account, IAM_CREATEROLE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Permissions.canAllocate( VENDOR_IAM, IAM_RESOURCE_ROLE, "", IAM_CREATEROLE, requestUser, 1L ) ) {
      throw new AuthException( AuthException.QUOTA_EXCEEDED );
    }
    return account.addRole( roleName, path, assumeRolePolicy );
  }

  public static boolean allowListRole( RequestUserContext requestUser, Account account, Role role ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_ROLE, IAM_LISTROLES ),
            account.getAccountNumber( ),
            Accounts.getRoleFullName( role ) );
  }

  public static boolean allowReadRole( User requestUser, Account account, Role role ) throws AuthException {
    return Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_GETROLE, requestUser );
  }

  public static void deleteRole( User requestUser, Account account, Role role ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_DELETEROLE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    account.deleteRole( role.getName( ) );
  }

  public static void updateAssumeRolePolicy( User requestUser, Account account, Role role, String assumeRolePolicy ) throws AuthException, PolicyParseException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_UPDATEASSUMEROLEPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    role.setAssumeRolePolicy( assumeRolePolicy );
  }

  public static InstanceProfile createInstanceProfile( User requestUser, Account account, String instanceProfileName, String path ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, "", account, IAM_CREATEINSTANCEPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( !Permissions.canAllocate( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, "", IAM_CREATEINSTANCEPROFILE, requestUser, 1L ) ) {
      throw new AuthException( AuthException.QUOTA_EXCEEDED );
    }
    return account.addInstanceProfile( instanceProfileName, path );
  }

  public static boolean allowListInstanceProfile( RequestUserContext requestUser, Account account, InstanceProfile instanceProfile ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, IAM_LISTINSTANCEPROFILES ),
            account.getAccountNumber( ),
            Accounts.getInstanceProfileFullName( instanceProfile ) );
  }

  public static boolean allowReadInstanceProfile( User requestUser, Account account, InstanceProfile instanceProfile ) throws AuthException {
    return Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, Accounts.getInstanceProfileFullName( instanceProfile ), account, IAM_GETINSTANCEPROFILE, requestUser );
  }

  public static void deleteInstanceProfile( User requestUser, Account account, InstanceProfile instanceProfile ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, Accounts.getInstanceProfileFullName( instanceProfile ), account, IAM_DELETEINSTANCEPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    account.deleteInstanceProfile( instanceProfile.getName( ) );
  }

  public static void addRoleToInstanceProfile( User requestUser, Account account, InstanceProfile instanceProfile, Role role ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, Accounts.getInstanceProfileFullName( instanceProfile ), account, IAM_ADDROLETOINSTANCEPROFILE, requestUser ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_ADDROLETOINSTANCEPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    final Role currentRole = instanceProfile.getRole();
    if ( currentRole != null && currentRole.getName().equals( role.getName() ) ) {
      throw new AuthException( AuthException.CONFLICT );
    }
    instanceProfile.setRole( role );
  }

  public static void removeRoleFromInstanceProfile( User requestUser, Account account, InstanceProfile instanceProfile, Role role ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, Accounts.getInstanceProfileFullName( instanceProfile ), account, IAM_REMOVEROLEFROMINSTANCEPROFILE, requestUser ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_REMOVEROLEFROMINSTANCEPROFILE, requestUser )) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    instanceProfile.setRole( null );
  }

  public static List<InstanceProfile> listInstanceProfilesForRole( User requestUser, Account account, Role role ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_LISTINSTANCEPROFILESFORROLE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return role.getInstanceProfiles();
  }

  public static boolean allowListInstanceProfileForRole( RequestUserContext requestUser, Account account, InstanceProfile instanceProfile ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_INSTANCE_PROFILE, IAM_LISTINSTANCEPROFILESFORROLE ),
            account.getAccountNumber( ),
            Accounts.getInstanceProfileFullName( instanceProfile ) );
  }

  public static void putAccountPolicy( User requestUser, Account account, String name, String policy ) throws AuthException, PolicyParseException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_PUTACCOUNTPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    // Can not add policy to system account "eucalyptus"
    if ( Account.SYSTEM_ACCOUNT.equals( account.getName( ) ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    User admin = account.lookupAdmin();
    admin.addPolicy( name, policy );
  }
  
  public static void putGroupPolicy( User requestUser, Account account, Group group, String name, String policy ) throws AuthException, PolicyParseException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_PUTGROUPPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    group.addPolicy( name, policy );
  }

  public static void putUserPolicy( User requestUser, Account account, User user, String name, String policy ) throws AuthException, PolicyParseException {
    // Policy attached to account admin is the account policy. Only system admin can put policy to an account.
    if ( user.isAccountAdmin( ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_PUTUSERPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    user.addPolicy( name, policy );
  }

  public static void putRolePolicy( User requestUser, Account account, Role role, String name, String policy ) throws AuthException, PolicyParseException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_PUTROLEPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    role.addPolicy( name, policy );
  }

  public static void deleteAccountPolicy( User requestUser, Account account, String name ) throws AuthException {
    if ( !requestUser.isSystemUser() ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ACCOUNT, Accounts.getAccountFullName(account), account, IAM_DELETEACCOUNTPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    User admin = account.lookupAdmin();
    admin.removePolicy( name );
  }
  
  public static void deleteGroupPolicy( User requestUser, Account account, Group group, String name ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_DELETEGROUPPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    group.removePolicy( name );
  }

  public static void deleteUserPolicy( User requestUser, Account account, User user, String name ) throws AuthException {
    // Policy attached to account admin is the account policy.
    if ( user.isAccountAdmin( ) ||
        !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_DELETEUSERPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    user.removePolicy( name );
  }

  public static void deleteRolePolicy( User requestUser, Account account, Role role, String name ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_DELETEROLEPOLICY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    role.removePolicy( name );
  }

  public static List<Policy> listAccountPolicies( User requestUser, Account account ) throws AuthException {
    if ( !allowListOrReadAccountPolicy( requestUser( requestUser ), account ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    User admin = account.lookupAdmin();
    return admin.getPolicies( );
  }
  
  public static List<Policy> listGroupPolicies( User requestUser, Account account, Group group ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_GROUP, Accounts.getGroupFullName( group ), account, IAM_LISTGROUPPOLICIES, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return group.getPolicies( );
  }

  public static List<Policy> listUserPolicies( User requestUser, Account account, User user ) throws AuthException {
    if ( !allowListUserPolicy( requestUser, account, user ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return user.getPolicies( );
  }

  public static List<Policy> listRolePolicies( User requestUser, Account account, Role role ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_LISTROLEPOLICIES, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return role.getPolicies( );
  }

  public static Policy getAccountPolicy( User requestUser, Account account, String policyName ) throws AuthException {
    if ( !allowListOrReadAccountPolicy( requestUser( requestUser ), account ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( policyName ) ) {
      throw new AuthException( AuthException.EMPTY_POLICY_NAME );
    }
    User admin = account.lookupAdmin();
    Policy policy = null;
    for ( Policy p : admin.getPolicies( ) ) {
      if ( p.getName( ).equals( policyName ) ) {
        policy = p;
        break;
      }
    }
    return policy;
  }
  
  public static Policy getGroupPolicy( User requestUser, Account account, Group group, String policyName ) throws AuthException {
    if ( !allowReadGroupPolicy( requestUser, account, group ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( policyName ) ) {
      throw new AuthException( AuthException.EMPTY_POLICY_NAME );
    }
    Policy policy = null;
    for ( Policy p : group.getPolicies( ) ) {
      if ( p.getName( ).equals( policyName ) ) {
        policy = p;
        break;
      }
    }
    return policy;
  }
  
  public static Policy getUserPolicy( User requestUser, Account account, User user, String policyName ) throws AuthException {
    if ( !allowReadUserPolicy( requestUser, account, user ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( policyName ) ) {
      throw new AuthException( AuthException.EMPTY_POLICY_NAME );
    }
    Policy policy = null;
    for ( Policy p : user.getPolicies( ) ) {
      if ( p.getName( ).equals( policyName ) ) {
        policy = p;
        break;
      }
    }    
    return policy;
  }

  public static Policy getRolePolicy( User requestUser, Account account, Role role, String policyName ) throws AuthException {
    if ( !allowReadRolePolicy( requestUser, account, role ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( policyName ) ) {
      throw new AuthException( AuthException.EMPTY_POLICY_NAME );
    }
    Policy policy = null;
    for ( Policy p : role.getPolicies( ) ) {
      if ( p.getName( ).equals( policyName ) ) {
        policy = p;
        break;
      }
    }
    return policy;
  }

  public static boolean allowReadGroupPolicy( User requestUser, Account account, Group group ) throws AuthException {
    return allowReadGroupPolicy( new RequestUserContext( requestUser ), account, group );
  }

  public static boolean allowReadGroupPolicy( RequestUserContext requestUser, Account account, Group group ) throws AuthException {
    return
        Permissions.isAuthorized(
            requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_GROUP, IAM_GETGROUPPOLICY ),
            account.getAccountNumber(),
            Accounts.getGroupFullName( group ) );
  }

  public static boolean allowListAndReadGroupPolicy( RequestUserContext requestUser, Account account, Group group ) throws AuthException {
    return !group.isUserGroup( ) && // we are not looking at a users policies and authorized
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_GROUP, IAM_LISTGROUPPOLICIES ), account.getAccountNumber(), Accounts.getGroupFullName( group ) ) &&
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_GROUP, IAM_GETGROUPPOLICY ), account.getAccountNumber(), Accounts.getGroupFullName( group ) );
  }

  public static boolean allowReadRolePolicy( User requestUser, Account account, Role role ) throws AuthException {
    return Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_ROLE, Accounts.getRoleFullName( role ), account, IAM_GETROLEPOLICY, requestUser );
  }

  public static boolean allowListUserPolicy( User requestUser, Account account, User user ) throws AuthException {
    return !user.isAccountAdmin( ) && // we are not looking at account admin's policies and authorized
        Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_LISTUSERPOLICIES, requestUser );
  }

  public static boolean allowReadUserPolicy( User requestUser, Account account, User user ) throws AuthException {
    return !user.isAccountAdmin( ) && // we are not looking at account admin's policies and authorized
        Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_GETUSERPOLICY, requestUser );
  }

  public static boolean allowListAndReadUserPolicy( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    return !user.isAccountAdmin( ) && // we are not looking at account admin's policies and authorized
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_LISTUSERPOLICIES ), account.getAccountNumber(), Accounts.getUserFullName( user ) ) &&
        Permissions.isAuthorized( requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_GETUSERPOLICY ), account.getAccountNumber(), Accounts.getUserFullName( user ) );
  }

  public static boolean allowListAccessKeys( RequestUserContext requestUser, Account account, User user ) {
    return Permissions.isAuthorized(
        requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_LISTACCESSKEYS ),
        account.getAccountNumber( ),
        Accounts.getUserFullName( user ) );
  }

  public static boolean allowListSigningCertificates( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    return Permissions.isAuthorized(
        requestUser.evaluationContext( VENDOR_IAM, IAM_RESOURCE_USER, IAM_LISTSIGNINGCERTIFICATES ),
        account.getAccountNumber( ),
        Accounts.getUserFullName( user ) );
  }

  public static AccessKey createAccessKey( User requestUser, Account account, User user ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_CREATEACCESSKEY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return user.createKey( );
  }

  public static void deleteAccessKey( User requestUser, Account account, User user, String keyId ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_DELETEACCESSKEY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    user.removeKey( keyId );
  }

  public static List<AccessKey> listAccessKeys( User requestUser, Account account, User user ) throws AuthException {
    return listAccessKeys( new RequestUserContext( requestUser ), account, user );
  }

  public static List<AccessKey> listAccessKeys( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    if ( !allowListAccessKeys( requestUser, account, user ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    return user.getKeys( );
  }

  public static void modifyAccessKey( User requestUser, Account account, User user, String keyId, String status ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATEACCESSKEY, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( keyId ) ) {
      throw new AuthException( AuthException.EMPTY_KEY_ID );
    }
    if ( Strings.isNullOrEmpty( status ) ) {
      throw new AuthException( AuthException.EMPTY_STATUS );
    }
    AccessKey key = user.getKey( keyId );
    key.setActive( "Active".equalsIgnoreCase( status ) );
  }
  
  public static Certificate createSigningCertificate( User requestUser, Account account, User user, KeyPair keyPair ) throws AuthException {
    // Use the official UPLOADSIGNINGCERTIFICATE action here
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPLOADSIGNINGCERTIFICATE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    X509Certificate x509 = Certs.generateCertificate( keyPair, user.getName( ) );
    try {
      x509.checkValidity( );
    } catch ( Exception e ) {
      throw new AuthException( "Invalid X509 Certificate", e );
    }
    return user.addCertificate( x509 );
  }

  public static Certificate uploadSigningCertificate( User requestUser, Account account, User user, String certBody ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPLOADSIGNINGCERTIFICATE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( certBody ) ) {
      throw new AuthException( AuthException.EMPTY_CERT );
    }
    String encodedPem = B64.url.encString( certBody );
    for ( Certificate c : user.getCertificates( ) ) {
      if ( c.getPem( ).equals( encodedPem ) ) {
        if ( !c.isRevoked( ) ) {
          throw new AuthException( AuthException.CONFLICT );        
        } else {
          user.removeCertificate( c.getCertificateId( ) );
        }
      }
    }
    X509Certificate x509 = X509CertHelper.toCertificate( encodedPem );
    if ( x509 == null ) {
      throw new AuthException( AuthException.INVALID_CERT );        
    }
    return user.addCertificate( x509 );
  }

  public static List<Certificate> listSigningCertificates( User requestUser, Account account, User user ) throws AuthException {
    return listSigningCertificates( new RequestUserContext( requestUser ), account, user );
  }

  public static List<Certificate> listSigningCertificates( RequestUserContext requestUser, Account account, User user ) throws AuthException {
    if ( !allowListSigningCertificates( requestUser, account, user ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    List<Certificate> certs = Lists.newArrayList( );
    for ( Certificate cert : user.getCertificates( ) ) {
      if ( !cert.isRevoked( ) ) {
        certs.add( cert );
      }
    }
    return certs;
  }

  public static void deleteSigningCertificate( User requestUser, Account account, User user, String certId ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_DELETESIGNINGCERTIFICATE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    user.removeCertificate( certId );
  }
  
  public static void modifySigningCertificate( User requestUser, Account account, User user, String certId, String status ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATESIGNINGCERTIFICATE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    if ( Strings.isNullOrEmpty( status ) ) {
      throw new AuthException( AuthException.EMPTY_STATUS );
    }
    if ( Strings.isNullOrEmpty( certId ) ) {
      throw new AuthException( AuthException.EMPTY_CERT_ID );
    }
    Certificate cert = user.getCertificate( certId );
    if ( cert.isRevoked( ) ) {
      throw new AuthException( AuthException.NO_SUCH_CERTIFICATE );
    }
    cert.setActive( "Active".equalsIgnoreCase( status ) );
  }

  public static void createLoginProfile( User requestUser, Account account, User user, String password ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_CREATELOGINPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    setUserPassword( user, password );
  }
  
  public static void deleteLoginProfile( User requestUser, Account account, User user ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_DELETELOGINPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    user.setPassword( null );
  }

  public static boolean allowReadLoginProfile( User requestUser, Account account, User user ) throws AuthException {
    return Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_GETLOGINPROFILE, requestUser );
  }

  public static void updateLoginProfile( User requestUser, Account account, User user, String newPass ) throws AuthException {
    if ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATELOGINPROFILE, requestUser ) ) {
      throw new AuthException( AuthException.ACCESS_DENIED );
    }
    setUserPassword( user, newPass );
  }

  public static boolean allowProcessUserSignup( User requestUser, User user ) throws AuthException {
    return requestUser.isSystemAdmin( ) ||
           ( requestUser.getAccount( ).getAccountNumber( ).equals( user.getAccount( ).getAccountNumber( ) ) &&
             requestUser.isAccountAdmin( ) );
  }

  private static void setUserPassword( User user, String newPass ) throws AuthException {
    if ( Strings.isNullOrEmpty( newPass ) || user.getName( ).equals( newPass ) ) {
      throw new AuthException( AuthException.INVALID_PASSWORD );
    }
    String newEncrypted = Crypto.generateEncryptedPassword( newPass );
    user.setPassword( newEncrypted );
    user.setPasswordExpires( System.currentTimeMillis( ) + User.PASSWORD_LIFETIME );
  }
  
  // Special case for change password. We should allow a user to do all the stuff here for himself without explicit permission.
  public static void changeUserPasswordAndEmail( User requestUser, Account account, User user, String newPass, String email ) throws AuthException {
    if ( !requestUser.isSystemAdmin( ) ) {
      if ( !requestUser.getAccount( ).getAccountNumber( ).equals( account.getAccountNumber( ) ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      }
      if ( !requestUser.getUserId( ).equals( user.getUserId( ) ) && 
           ( !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATELOGINPROFILE, requestUser ) ||
             ( email != null &&
               !Permissions.isAuthorized( VENDOR_IAM, IAM_RESOURCE_USER, Accounts.getUserFullName( user ), account, IAM_UPDATEUSER, requestUser ) ) ) ) {
        throw new AuthException( AuthException.ACCESS_DENIED );
      }
    }
    setUserPassword( user, newPass );
    if ( email != null ) {
      user.setInfo( User.EMAIL, email );
    }
  }
  
  public static void createServerCertificate(final User requestUser, final String pemCertBody, final String pemCertChain, 
      final String path, final String certName, final String pemPk) throws AuthException {
    final Account acct = requestUser.getAccount();
    
    if ( !requestUser.isSystemAdmin( ) ) {
       if ( !Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_UPLOADSERVERCERTIFICATE, certName, acct, PolicySpec.IAM_UPLOADSERVERCERTIFICATE, requestUser ))
            throw new AuthException( AuthException.ACCESS_DENIED );
    }
    
    try{
      acct.addServerCertificate(certName, pemCertBody, pemCertChain, path, pemPk);
    }catch(final AuthException ex){
      throw ex;
    }catch(final Exception ex){
      throw ex;
    }
  }
  
  public static List<ServerCertificate> listServerCertificate(final User requestUser, final String pathPrefix) throws AuthException {
    final Account acct = requestUser.getAccount();
    if ( !requestUser.isSystemAdmin( ) ) {
       if ( !Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_LISTSERVERCERTIFICATES, pathPrefix, acct, PolicySpec.IAM_LISTSERVERCERTIFICATES, requestUser ))
            throw new AuthException( AuthException.ACCESS_DENIED );
    }
    try{
      return acct.listServerCertificates(pathPrefix);
    }catch(final AuthException ex){
      throw ex;
    }catch(final Exception ex){
      throw ex;
    }
  }
  
  public static ServerCertificate getServerCertificate(final User requestUser, final String certName) throws AuthException {
    final Account acct = requestUser.getAccount();
    if ( !requestUser.isSystemAdmin( ) ) {
       if ( !Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_GETSERVERCERTIFICATE, certName, acct, PolicySpec.IAM_GETSERVERCERTIFICATE, requestUser ))
            throw new AuthException( AuthException.ACCESS_DENIED );
    }
    try{
      return acct.lookupServerCertificate(certName);
    }catch(final AuthException ex){
      throw ex;
    }catch(final Exception ex){
      throw ex;
    }
  }
  
  public static void deleteServerCertificate(final User requestUser, final String certName) throws AuthException{
    final Account acct = requestUser.getAccount();
    if ( !requestUser.isSystemAdmin( ) ) {
       if ( !Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_DELETESERVERCERTIFICATE, certName, acct, PolicySpec.IAM_DELETESERVERCERTIFICATE, requestUser ))
            throw new AuthException( AuthException.ACCESS_DENIED );
    }
    try{
      acct.deleteServerCertificate(certName);
    }catch(final AuthException ex){
      throw ex;
    }catch(final Exception ex){
      throw ex;
    }
  }
  
  public static void updateServerCertificate(final User requestUser, final String certName, final String newCertName, final String newPath) 
    throws AuthException
  {
    final Account acct = requestUser.getAccount();
    if ( !requestUser.isSystemAdmin( ) ) {
       if ( ! ( Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_UPDATESERVERCERTIFICATE, certName, acct, PolicySpec.IAM_UPDATESERVERCERTIFICATE, requestUser ) &&
             Permissions.isAuthorized( PolicySpec.VENDOR_IAM, PolicySpec.IAM_UPDATESERVERCERTIFICATE, newCertName, acct, PolicySpec.IAM_UPDATESERVERCERTIFICATE, requestUser )))
            throw new AuthException( AuthException.ACCESS_DENIED );
    }
   try{
     acct.updateServerCeritificate(certName, newCertName, newPath);
   }catch(final AuthException ex){
     throw ex;
   }catch(final Exception ex){
     throw ex;
   }
  }
} 
