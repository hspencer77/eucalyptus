/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
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

@GroovyAddClassUUID
package edu.ucsb.eucalyptus.msgs

public class DnsMessage extends BaseMessage {}
public class DNSResponseType extends DnsMessage {
  def DNSResponseType() {}
}

public class DNSRequestType extends DnsMessage {

  def DNSRequestType() {}
}

public class CreateMultiARecordType extends DNSRequestType {
	String zone;
	String name;
	long ttl;
	
    def CreateMultiARecordType() {}

    def CreateMultiARecordType(String zone, String name, long ttl) {
		this.zone = zone;
		this.name = name;
		this.ttl = ttl;
    }
}

public class CreateMultiARecordResponseType extends DNSResponseType {
  def CreateMultiARecordResponseType() {}
}


public class AddMultiARecordType extends DNSRequestType {
	String zone;
	String name;
	String address;
	long ttl;
	
	def AddMultiARecordType() {}

	def AddMultiARecordType(String zone, String name, String address, long ttl) {
		this.zone = zone;
		this.name = name;
		this.address = address;
		this.ttl = ttl;
	}
}

public class AddMultiARecordResponseType extends DNSResponseType {
  def AddMultiARecordResponseType() {}
}

public class RemoveMultiARecordType extends DNSRequestType {
	String zone;
	String name;
	String address;
	
	def RemoveMultiARecordType() {}

	def RemoveMultiARecordType(String zone, String name, String address) {
		this.zone = zone;
		this.name = name;
		this.address = address;
	}
}

public class RemoveMultiARecordResponseType extends DNSResponseType {
  def RemoveMultiARecordResponseType() {}
}


public class RemoveMultiANameType extends DNSRequestType {
	String zone;
	String name;
	
	def RemoveMultiANameType() {}

	def RemoveMultiANameType(String zone, String name) {
		this.zone = zone;
		this.name = name;
	}
}

public class RemoveMultiANameResponseType extends DNSResponseType {
  def RemoveMultiANameResponseType() {}
}

public class UpdateARecordType extends DNSRequestType {
  String zone;
  String name;
  String address;
  long ttl;

  def UpdateARecordType() {}

  def UpdateARecordType(String zone, String name, String address, long ttl) {
      this.zone = zone;
      this.name = name;
      this.address = address;
      this.ttl = ttl;
  }
}

public class UpdateARecordResponseType extends DNSResponseType {
  def UpdateARecordResponseType() {}
}

public class RemoveARecordType extends DNSRequestType {
  String zone;
  String name;
  String address;
  
  def RemoveARecordType() {}

  def RemoveARecordType(String zone, String name, String address) {
      this.zone = zone;
      this.name = name;
      this.address = address;
  }
}

public class RemoveARecordResponseType extends DNSResponseType {
  def RemoveARecordResponseType() {}
}

public class UpdateCNAMERecordType extends DNSRequestType {
  String zone;
  String name;
  String alias;
  long ttl;

  def UpdateCNAMERecordType() {}

  def UpdateCNAMERecordType(String zone, String name, String alias, long ttl) {
      this.zone = zone;
      this.name = name;
      this.alias = alias;
      this.ttl = ttl;
  }
}

public class UpdateCNAMERecordResponseType extends DNSResponseType {
  def UpdateCNAMERecordResponseType() {}
}

public class RemoveCNAMERecordType extends DNSRequestType {
  String zone;
  String name;
  String alias;
  
  def RemoveCNAMERecordType() {}

  def RemoveCNAMERecordType(String zone, String name, String alias) {
      this.zone = zone;
      this.name = name;
      this.alias = alias;
  }
}

public class RemoveCNAMERecordResponseType extends DNSResponseType {
  def RemoveCNAMERecordResponseType() {}
}

public class AddZoneType extends DNSRequestType {
  String name;
  def AddZoneType() {}
  def AddZoneType(String name) {
    this.name = name;
  }
}

public class AddZoneResponseType extends DNSResponseType {
}

public class DeleteZoneType extends DNSRequestType {
  String name;
  def DeleteZoneType() {}
  def DeleteZoneType(String name) {
    this.name = name;
  }
}

public class DeleteZoneResponseType extends DNSResponseType {
}
