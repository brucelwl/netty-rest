package com.lwl.entity;


import java.io.Serializable;

public class UserInfo implements Serializable {
	private static final long serialVersionUID = -7365258998459072131L;

	private int id;
	private String loginname;
	private String pwd;
	private String nickname;
	private Address address;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getLoginname() {
		return loginname;
	}

	public void setLoginname(String loginname) {
		this.loginname = loginname;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	@Override
	public String toString() {
		return "UserInfo{" +
				"id=" + id +
				", loginname='" + loginname + '\'' +
				", pwd='" + pwd + '\'' +
				", nickname='" + nickname + '\'' +
				", address=" + address +
				'}';
	}
}
